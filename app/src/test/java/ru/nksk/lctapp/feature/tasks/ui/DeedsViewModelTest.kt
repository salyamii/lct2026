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
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

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

    private suspend fun fixture(): Triple<DeedsRepository, DeedsViewModel, GameSession> {
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
        val session = GameSession(repository, content, catalog, initial)
        assertTrue(session.dispatch(EngineRequest("begin", null,
            EngineCommand.BeginDay(catalog.storyDayId, catalog.deedPool.take(4), openFirst = true))) is EngineResult.Applied)
        assertTrue(session.dispatch(EngineRequest("later", repository.read().engine!!.revision,
            EngineCommand.DismissDeedProposal(repository.read().engine!!.currentEvent!!.id))) is EngineResult.Applied)
        val model = DeedsViewModel(session)
        store.put("deeds", model)
        return Triple(repository, model, session)
    }
}

private class DeedsRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow(initial)
    var afterWrite: suspend () -> Unit = {}
    var failure: Exception? = null
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        failure?.let { throw it }
        val next = transform(state.value)
        state.value = next
        afterWrite()
        return next
    }
}
