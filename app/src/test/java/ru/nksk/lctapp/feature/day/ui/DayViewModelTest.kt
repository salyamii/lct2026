package ru.nksk.lctapp.feature.day.ui

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
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineResult
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun laterKeepsTheCardWhenObservationArrivesBeforeDispatchReturns() = runTest(dispatcher) {
        val (repository, model) = fixture()
        runCurrent()
        val card = model.uiState.value
        val states = mutableListOf<DayUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.uiState.collect { states += it }
        }
        var exits = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits++ } }
        val dispatchCanReturn = CompletableDeferred<Unit>()
        repository.afterWrite = { dispatchCanReturn.await() }

        model.onAction(DayAction.Later)
        runCurrent()
        assertNull(repository.read().engine!!.currentEvent)
        assertEquals(EventStatus.PAUSED, repository.read().engine!!.events.first().status)
        assertEquals(0, exits)
        assertEquals(card.copy(busy = true, message = null), model.uiState.value)

        dispatchCanReturn.complete(Unit)
        runCurrent()
        assertEquals(1, exits)
        assertEquals(card.copy(busy = true, message = null), model.uiState.value)
        assertTrue(states.all { it.title == card.title && it.body == card.body && it.scene == card.scene })
        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(1, repository.writes)
    }

    @Test fun choosingCommitsTheResultAndLeavesWithoutShowingAnIntermediateCard() = runTest(dispatcher) {
        val (repository, model) = fixture()
        runCurrent()
        val card = model.uiState.value
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }

        model.onAction(DayAction.Choose(model.uiState.value.options.first().id))
        runCurrent()
        assertNull(repository.read().engine!!.currentEvent)
        assertEquals(EventStatus.COMPLETED, repository.read().engine!!.events.first().status)
        assertEquals(1, repository.read().story.decisions.size)
        assertEquals(listOf("Событие выполнено!"), exits)
        assertEquals(card.copy(busy = true, message = null), model.uiState.value)
    }

    @Test fun failedPauseUnlocksTheCardAndAllowsRetryWithoutLeaving() = runTest(dispatcher) {
        val (repository, model) = fixture()
        runCurrent()
        val card = model.uiState.value
        var exits = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits++ } }
        repository.failure = IOException("Write unavailable")

        model.onAction(DayAction.Later)
        runCurrent()
        assertEquals(0, exits)
        assertFalse(model.uiState.value.busy)
        assertEquals(card.title, model.uiState.value.title)
        assertNotNull(model.uiState.value.message)
        assertEquals(EventStatus.ACTIVE, repository.read().engine!!.currentEvent!!.status)

        repository.failure = null
        model.onAction(DayAction.Later)
        runCurrent()
        assertEquals(1, exits)
        assertEquals(card.copy(busy = true, message = null), model.uiState.value)
    }

    @Test fun hungerReplacesThePrimaryActionWithoutASecondFeedButton() = runTest(dispatcher) {
        val (repository, model) = fixture()
        repository.update { game -> game.copy(engine = game.engine!!.let { day ->
            day.copy(steps = 3, events = day.events.map {
                if (it.status == EventStatus.ACTIVE) it.copy(status = EventStatus.PAUSED) else it
            })
        }, story = game.story.copy(activeEventId = null)) }
        runCurrent()
        assertTrue(model.uiState.value.primaryNeedsFood)
        assertEquals("Покормить", model.uiState.value.primary)
    }

    @Test fun unblockedEventDoesNotOpenFeedingButHungerReplacesItsAction() = runTest(dispatcher) {
        val (repository, model, session) = fixture(deedFirst = true)
        runCurrent()
        assertFalse(model.uiState.value.options.single().needsFood)
        model.onAction(DayAction.ShowMeals)
        assertFalse(model.uiState.value.showMeals)

        repository.update { it.copy(engine = it.engine!!.copy(steps = 3)) }
        runCurrent()
        assertTrue(model.uiState.value.options.single().needsFood)
        model.onAction(DayAction.ShowMeals)
        assertTrue(model.uiState.value.showMeals)
        model.onAction(DayAction.Feed(session.catalog.meals.first { it.price > 0 }.id))
        runCurrent()
        assertFalse(model.uiState.value.showMeals)
        assertFalse(model.uiState.value.options.single().needsFood)
        assertEquals("Выполнить дело", model.uiState.value.options.single().label)
        assertEquals(EventOrigin.SCHEDULE, repository.read().engine!!.currentEvent!!.origin)
        assertEquals(3, repository.read().engine!!.steps)
    }

    @Test fun wakingReturnsToMenuBeforeAnyEventIsOpened() = runTest(dispatcher) {
        val (repository, model, session) = fixture(finishedDay = 1)
        runCurrent()
        val summary = model.uiState.value
        val previous = repository.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        val canReturn = CompletableDeferred<Unit>()
        repository.afterWrite = { canReturn.await() }

        model.onAction(DayAction.Primary)
        runCurrent()
        val morning = repository.read()
        assertEquals(2, morning.engine!!.day)
        assertEquals(0, morning.engine!!.steps)
        assertEquals(5, morning.engine!!.energy)
        assertFalse(morning.engine!!.ateToday)
        assertNull(morning.engine!!.currentEvent)
        assertEquals(previous.engine!!.events.map { it.id }, morning.engine!!.events.map { it.id })
        assertEquals(previous.engine!!.deeds, morning.engine!!.deeds)
        assertEquals(previous.economy, morning.economy)
        assertTrue(exits.isEmpty())
        assertEquals(summary.copy(busy = true, message = null), model.uiState.value)

        canReturn.complete(Unit)
        runCurrent()
        assertEquals(listOf<String?>(null), exits)
        assertEquals(summary.copy(busy = true, message = null), model.uiState.value)
        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(1, repository.writes)

        assertEquals(EngineCommand.OpenNextEvent, session.advanceCommand(morning))
        assertTrue(session.dispatch(EngineRequest("continue-from-menu", morning.engine!!.revision,
            EngineCommand.OpenNextEvent)) is EngineResult.Applied)
        assertEquals(morning.engine!!.events.first().id, repository.read().engine!!.currentEvent!!.id)
    }

    @Test fun failedMorningCanRetryWithoutDuplicatingWeeklyIncomeOrOpeningAnEvent() = runTest(dispatcher) {
        val (repository, model, session) = fixture(finishedDay = 7)
        runCurrent()
        val previous = repository.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        repository.failure = IOException("Morning commit failed")
        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(previous, repository.read())
        assertTrue(exits.isEmpty())
        assertFalse(model.uiState.value.busy)
        assertNotNull(model.uiState.value.message)

        repository.failure = null
        model.onAction(DayAction.Primary)
        runCurrent()
        val morning = repository.read()
        assertEquals(8, morning.engine!!.day)
        assertEquals(previous.economy.balance + 100, morning.economy.balance)
        assertNull(morning.engine!!.currentEvent)
        assertEquals(0, morning.engine!!.steps)
        assertEquals(listOf<String?>(null), exits)
        val restored = DayViewModel(session)
        store.put("restored-day", restored)
        runCurrent()
        assertEquals(morning, repository.read())
        assertEquals(1, repository.writes)
    }

    @Test fun deferringADeedLeavesWithoutACompletionMessage() = runTest(dispatcher) {
        val (repository, model) = fixture(deedFirst = true)
        runCurrent()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        model.onAction(DayAction.Later)
        runCurrent()
        assertEquals(listOf<String?>(null), exits)
        assertFalse(repository.read().engine!!.deeds.single().completed)
    }

    @Test fun acceptingTheDeedOpensItsGameWithoutAwardingMoneyOrClosingTheDeed() = runTest(dispatcher) {
        val (repository, model) = fixture(deedFirst = true)
        runCurrent()
        val games = mutableListOf<String>()
        var exits = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openGame.collect { games += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits++ } }

        model.onAction(DayAction.Choose(model.uiState.value.options.single().id))
        runCurrent()
        val saved = repository.read()
        assertEquals(listOf(saved.engine!!.currentEvent!!.id), games)
        assertEquals(EventOrigin.DEED, saved.engine!!.currentEvent!!.origin)
        assertEquals(100L, saved.economy.balance)
        assertEquals(5, saved.engine!!.energy)
        assertEquals(1, saved.engine!!.steps)
        assertFalse(saved.engine!!.deeds.single().completed)
        assertEquals(0, exits)
    }

    @Test fun purchaseOffersPassingByWithoutPausingAndWarnsAboutFoodMoney() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        repository.update { it.copy(economy = it.economy.copy(balance = 27)) }
        runCurrent()
        assertEquals(listOf("Купить · 25", "Пройти мимо"), model.uiState.value.options.map { it.label })
        assertNull(model.uiState.value.later)
        assertTrue(model.uiState.value.message!!.contains("обед"))
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        model.onAction(DayAction.Choose("figma-2164-2-v1:pass"))
        runCurrent()
        assertEquals(listOf("Событие выполнено!"), exits)
        assertNull(repository.read().engine!!.currentEvent)
        assertEquals(27L, repository.read().economy.balance)
    }

    @Test fun hungryRepairRequiresFoodThenRestoresBothChoicesWithoutExecutingThem() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2313-2-v1")
        repository.update { it.copy(engine = it.engine!!.copy(steps = 3, ateToday = false)) }
        runCurrent()
        assertEquals(2, model.uiState.value.options.size)
        assertTrue(model.uiState.value.options.all { it.needsFood })
        model.onAction(DayAction.ShowMeals)
        runCurrent()
        assertTrue(model.uiState.value.showMeals)
        model.onAction(DayAction.Feed("basic-v1"))
        runCurrent()
        assertTrue(model.uiState.value.options.all { it.enabled && !it.needsFood })
        assertEquals(95L, repository.read().economy.balance)
        assertEquals(5, repository.read().engine!!.energy)
        assertEquals(3, repository.read().engine!!.steps)
        assertTrue(repository.read().story.decisions.isEmpty())
    }

    private suspend fun fixture(deedFirst: Boolean = false, finishedDay: Int? = null,
        eventFirst: String? = null): Triple<DayRepository, DayViewModel, GameSession> {
        val initial = createInitialGameState()
        val repository = DayRepository(initial)
        val catalog = bundledGameCatalog()
        val content = object : StoryContentRepository {
            private var stored = StoryContent()
            override suspend fun read() = stored
            override suspend fun install(content: StoryContent) { stored = content }
        }
        val session = GameSession(repository, content, catalog, initial)
        assertTrue(session.dispatch(EngineRequest("begin", null,
            EngineCommand.BeginDay(catalog.storyDayId, when {
                eventFirst != null -> listOf(eventFirst) + catalog.deedPool.take(3)
                deedFirst -> catalog.deedPool.take(4)
                else -> catalog.plan(initial)
            },
                openFirst = true))) is EngineResult.Applied)
        if (finishedDay != null) repository.update { saved ->
            saved.copy(story = saved.story.copy(activeEventId = null), engine = saved.engine!!.let { day ->
                day.copy(day = finishedDay, phase = DayPhase.FINISHED, energy = 0, steps = 4, ateToday = true,
                    events = day.events.map { it.copy(status = if (it.status == EventStatus.ACTIVE)
                        EventStatus.CARRIED_ACTIVE else EventStatus.CARRIED) })
            })
        }
        repository.writes = 0
        val model = DayViewModel(session)
        store.put("day", model)
        return Triple(repository, model, session)
    }
}

private class DayRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow(initial)
    var afterWrite: suspend () -> Unit = {}
    var failure: Exception? = null
    var writes = 0
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        failure?.let { throw it }
        val next = transform(state.value)
        state.value = next
        writes++
        afterWrite()
        return next
    }
}
