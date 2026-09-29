package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.demo.DemoPreferences
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.domain.demo.DisabledDemoPreferencesRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

@OptIn(ExperimentalCoroutinesApi::class)
class DeedsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun startingKeepsTheListUntilNavigationAndReturningRefreshesWithoutExtendingTheOffer() = runTest(dispatcher) {
        val (repository, model, session) = fixture()
        runCurrent()
        val card = model.uiState.value
        val offer = repository.read().engine!!.deeds.single()
        val routes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openEvent.collect { routes += it } }
        val release = CompletableDeferred<Unit>()
        repository.afterWrite = { release.await() }
        model.start(offer.id)
        runCurrent()
        assertTrue(routes.isEmpty())
        assertNotNull(repository.read().engine!!.currentEvent)
        assertEquals(card.copy(busy = true), model.uiState.value)

        release.complete(Unit)
        runCurrent()
        assertEquals(1, routes.size)
        assertEquals(card.copy(busy = true), model.uiState.value)
        model.onScreenResumed()
        assertTrue(model.uiState.value.busy)
        model.onScreenHidden()
        val active = repository.read().engine!!.currentEvent!!
        assertTrue(session.dispatch(EngineRequest("pause", repository.read().engine!!.revision,
            EngineCommand.PauseEvent(active.id))) is EngineResult.Applied)
        runCurrent()
        model.onScreenResumed()
        assertFalse(model.uiState.value.busy)
        assertFalse(model.uiState.value.hasCurrentEvent)
        assertEquals(offer, repository.read().engine!!.deeds.single())
        assertEquals(card.offers, model.uiState.value.offers)
    }

    @Test fun failedStartUnlocksTheExistingOffer() = runTest(dispatcher) {
        val (repository, model) = fixture()
        runCurrent()
        val before = repository.read()
        val offers = model.uiState.value.offers
        repository.failure = IOException("Write unavailable")
        model.start(offers.single().id)
        runCurrent()
        assertEquals(before, repository.read())
        assertFalse(model.uiState.value.busy)
        assertEquals(offers, model.uiState.value.offers)
        assertNotNull(model.uiState.value.message)
    }

    @Test fun rejectedStartExplainsTheActiveEventAndCanBeDismissedWithoutChangingTheWorld() = runTest(dispatcher) {
        val (repository, model, session) = fixture()
        runCurrent()
        val offer = model.uiState.value.offers.single()
        assertTrue(session.dispatch(EngineRequest("next-proposal", repository.read().engine!!.revision,
            EngineCommand.OpenNextEvent)) is EngineResult.Applied)
        runCurrent()
        val before = repository.read()

        model.start(offer.id)
        runCurrent()

        assertEquals(before, repository.read())
        assertFalse(model.uiState.value.busy)
        assertTrue(model.uiState.value.hasCurrentEvent)
        assertEquals("Сначала закончи текущее событие или выбери «Вернуться позже».", model.uiState.value.message)
        model.dismissMessage()
        assertNull(model.uiState.value.message)
        assertTrue(model.uiState.value.meals.isEmpty())
        assertEquals(before, repository.read())
    }

    @Test fun uncertainFreeMealKeepsItsOfferAndRequestAfterDemoModeIsDisabled() = runTest(dispatcher) {
        val preferences = MutableDemoPreferences(true)
        val (repository, model, session) = fixture(preferences)
        runCurrent()
        repository.update { it.copy(engine = it.engine!!.copy(steps = session.catalog.rules.hungerBlocksAtStep)) }
        runCurrent()
        model.start(model.uiState.value.offers.single().id)
        runCurrent()
        val chosen = model.uiState.value.meals.single { it.id == "luxury-v1" }
        assertTrue(chosen.enabled)
        assertEquals("Бесплатно", chosen.priceLabel)
        val before = repository.read()

        repository.failAfterCommit = true
        model.feed(chosen.id)
        runCurrent()
        val committed = repository.read()
        assertTrue(committed.engine!!.ateToday)
        assertEquals(before.economy, committed.economy)
        assertNotNull(model.uiState.value.message)
        assertEquals(listOf(chosen), model.uiState.value.meals)
        val original = repository.committed.single { it.command is EngineCommand.Feed }
        assertTrue(original.demoMode)

        preferences.setDemoModeEnabled(false)
        runCurrent()
        assertFalse(session.demoModeEnabled)
        assertEquals(listOf(chosen), model.uiState.value.meals)
        model.dismissMessage()
        model.feed("basic-v1")
        runCurrent()
        assertNotNull(model.uiState.value.message)
        assertEquals(listOf(chosen), model.uiState.value.meals)
        assertEquals(listOf(original), repository.attempted.filter { it.command is EngineCommand.Feed })

        model.feed(chosen.id)
        runCurrent()
        assertEquals(listOf(original, original), repository.attempted.filter { it.command is EngineCommand.Feed })
        assertEquals(listOf(original), repository.committed.filter { it.command is EngineCommand.Feed })
        assertEquals(committed, repository.read())
        val receipt = checkNotNull(session.commandReceipt(original.id))
        assertEquals(before.economy, receipt.before!!.economy)
        assertEquals(before.economy, receipt.after!!.economy)
        assertNull(model.uiState.value.message)
        assertTrue(model.uiState.value.meals.isEmpty())
        assertFalse(model.uiState.value.busy)
    }

    @Test fun disablingDemoBeforeSubmittingAFreeMealCannotTurnItIntoAPaidPurchase() = runTest(dispatcher) {
        val preferences = MutableDemoPreferences(true)
        val (repository, model, session) = fixture(preferences)
        runCurrent()
        repository.update { it.copy(engine = it.engine!!.copy(steps = session.catalog.rules.hungerBlocksAtStep)) }
        runCurrent()
        model.start(model.uiState.value.offers.single().id)
        runCurrent()
        assertEquals("Бесплатно", model.uiState.value.meals.single { it.id == "luxury-v1" }.priceLabel)
        val before = repository.read()

        preferences.setDemoModeEnabled(false)
        // Submit the still-visible offer before its preference observation is rendered.
        model.feed("luxury-v1")
        runCurrent()

        assertEquals(before, repository.read())
        assertTrue(repository.committed.none { it.command is EngineCommand.Feed })
        assertNotNull(model.uiState.value.message)
        assertFalse(model.uiState.value.busy)
    }

    private suspend fun fixture(demoPreferences: DemoPreferencesRepository = DisabledDemoPreferencesRepository): Triple<DeedsRepository, DeedsViewModel, GameSession> {
        val catalog = bundledGameCatalog()
        val initial = createInitialGameState().copy(economy = EconomyState(
            plan = BudgetPlan(35, 20, 20, 25), availableBalance = 100, savingsBalance = 0,
            unallocated = 0, planning = null))
        val repository = DeedsRepository(initial)
        val content = object : StoryContentRepository {
            private var stored = StoryContent()
            override suspend fun read() = stored
            override suspend fun install(content: StoryContent) { stored = content }
        }
        val session = GameSession(repository, content, catalog, initial, demoPreferences)
        assertTrue(session.dispatch(EngineRequest("begin", null,
            EngineCommand.BeginDay(catalog.storyDayId, catalog.deedPool.take(4), openFirst = true))) is EngineResult.Applied)
        assertTrue(session.dispatch(EngineRequest("later", repository.read().engine!!.revision,
            EngineCommand.DismissDeedProposal(repository.read().engine!!.currentEvent!!.id))) is EngineResult.Applied)
        val model = DeedsViewModel(session)
        store.put("deeds", model)
        return Triple(repository, model, session)
    }

    private class MutableDemoPreferences(enabled: Boolean) : DemoPreferencesRepository {
        private val state = MutableStateFlow(DemoPreferences(enabled))
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun setDemoModeEnabled(enabled: Boolean) { state.value = DemoPreferences(enabled) }
    }
}

private class DeedsRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow(initial)
    var afterWrite: suspend () -> Unit = {}
    var failure: Exception? = null
    var failAfterCommit = false
    val attempted = mutableListOf<EngineRequest>()
    val committed = mutableListOf<EngineRequest>()
    private data class Receipt(val request: EngineRequest, val context: DecisionContext?, val fingerprint: String?)
    private val receipts = mutableMapOf<String, Receipt>()
    private val history = mutableListOf(AuditEntry("initial", 1, "run", AuditType.INITIALIZED, after = initial))
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun readHistory() = history.toList()
    override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
        facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
        attempted += request
        val identity = Receipt(request, context, contentFingerprint)
        receipts[request.id]?.let { prior ->
            check(prior == identity) { "Conflicting command identity" }
            return state.value
        }
        failure?.let { throw it }
        val before = state.value
        val next = transform(before)
        receipts[request.id] = identity
        committed += request
        history += AuditEntry("command:run:${request.id}", history.last().sequence + 1, "run", AuditType.COMMAND,
            request = request, context = context, before = before, after = next, contentFingerprint = contentFingerprint)
        state.value = next
        afterWrite()
        if (failAfterCommit) throw IOException("Committed reply was lost")
        return next
    }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        failure?.let { throw it }
        val next = transform(state.value)
        state.value = next
        afterWrite()
        return next
    }
}
