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
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineResult
import ru.nksk.lctapp.domain.engine.BlockReason
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.EventLayout
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.DeedOffer
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.TargetStopState

@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun exhaustedCardOffersOneFeedThenRestAndWakesInMenuWithoutOpeningAnEvent() = runTest(dispatcher) {
        val (repository, model) = fixture()
        repository.update { it.copy(economy = it.economy.copy(plan = BudgetPlan(0, 0, 0, 0),
            availableBalance = 0, savingsBalance = 0, unallocated = 0),
            engine = it.engine!!.copy(energy = 0, steps = 0, ateToday = false)) }
        runCurrent()
        val eventId = repository.read().engine!!.currentEvent!!.id
        var exits = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits++ } }
        assertEquals("Покормить", model.uiState.value.primary)
        assertTrue(model.uiState.value.primaryNeedsFood)
        assertTrue(model.uiState.value.options.isEmpty())
        assertTrue(model.uiState.value.actionNotice!!.startsWith("Рыжик устал."))
        assertTrue(model.uiState.value.actionNotice!!.contains("нужно поесть"))
        model.onAction(DayAction.ShowMeals)
        assertTrue(model.uiState.value.showMeals)
        val freeMeal = model.uiState.value.meals.single { it.label.startsWith("Бесплатная") }
        model.onAction(DayAction.Feed(freeMeal.id))
        runCurrent()
        assertFalse(model.uiState.value.showMeals)
        assertFalse(model.uiState.value.primaryNeedsFood)
        assertEquals("Закончить день", model.uiState.value.primary)
        assertTrue(model.uiState.value.options.isEmpty())
        assertTrue(model.uiState.value.actionNotice!!.startsWith("Рыжик устал."))
        assertTrue(model.uiState.value.actionNotice!!.contains("нужно отдохнуть"))
        assertFalse(model.uiState.value.actionNotice!!.contains("нужно поесть"))
        assertEquals(0, repository.read().engine!!.energy)
        assertEquals(eventId, repository.read().engine!!.currentEvent!!.id)

        val beforeRest = repository.read()
        val writes = repository.writes
        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(writes + 1, repository.writes)
        assertEquals(DayPhase.FINISHED, repository.read().engine!!.phase)
        assertEquals("День 1 завершён", model.uiState.value.title)
        assertEquals("Начать день 2", model.uiState.value.primary)
        assertNull(model.uiState.value.actionNotice)
        assertEquals(beforeRest.story.decisions, repository.read().story.decisions)
        assertEquals(0, exits)

        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(1, exits)
        assertEquals(2, repository.read().engine!!.day)
        assertEquals(3, repository.read().engine!!.energy)
        assertNull(repository.read().engine!!.currentEvent)
        assertEquals(eventId, repository.read().engine!!.events.first().id)
        assertEquals(EventStatus.PAUSED, repository.read().engine!!.events.first().status)
    }

    @Test fun affordableRepairAlternativeRemainsPlayableInsteadOfForcingRest() = runTest(dispatcher) {
        val (repository, model, session) = fixture(eventFirst = "figma-2313-2-v1")
        repository.update { it.copy(engine = it.engine!!.copy(energy = 1, ateToday = true)) }
        runCurrent()
        val occurrence = repository.read().engine!!.currentEvent!!
        assertEquals(BlockReason.UnfinishedEvents,
            session.engine.blockReason(repository.read(), EngineCommand.FinishDayFromEvent(occurrence.id)))
        assertNull(model.uiState.value.primary)
        assertNull(model.uiState.value.message)
        assertEquals(2, model.uiState.value.options.size)
        assertEquals(1, model.uiState.value.options.count { it.enabled })
        val available = model.uiState.value.options.single { it.enabled }
        val choice = session.catalog.content.choices.single { it.id == available.id }
        val before = repository.read()
        model.onAction(DayAction.Choose(available.id))
        runCurrent()
        assertEquals(before.economy.balance + choice.moneyDelta, repository.read().economy.balance)
        assertNull(repository.read().engine!!.currentEvent)
        assertEquals(1, repository.read().story.decisions.size)
    }

    @Test fun failedRestKeepsTheCardAndRetryClosesTheProposalWithoutStartingItsGame() = runTest(dispatcher) {
        val (repository, model) = fixture(deedFirst = true)
        repository.update { it.copy(engine = it.engine!!.copy(energy = 0, ateToday = true)) }
        runCurrent()
        val before = repository.read()
        val card = model.uiState.value
        var games = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openGame.collect { games++ } }
        repository.failure = IOException("Write unavailable")
        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(before, repository.read())
        assertEquals(card.title, model.uiState.value.title)
        assertEquals("Закончить день", model.uiState.value.primary)
        assertFalse(model.uiState.value.busy)
        assertTrue(model.uiState.value.message!!.contains("Не удалось сохранить"))
        assertEquals(card.actionNotice, model.uiState.value.actionNotice)
        repository.failure = null
        model.onAction(DayAction.Primary)
        runCurrent()
        assertEquals(DayPhase.FINISHED, repository.read().engine!!.phase)
        assertEquals(before.engine!!.deeds, repository.read().engine!!.deeds)
        assertEquals(before.engine!!.steps, repository.read().engine!!.steps)
        assertEquals(before.economy, repository.read().economy)
        assertEquals(0, games)
    }

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
        assertEquals(EventLayout.INTRODUCTION, card.layout)
        assertEquals("В обсерваторию", card.options.single().label)
        assertTrue(card.effort.isBlank())
        assertNull(card.financialContext)
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }

        model.onAction(DayAction.Choose(model.uiState.value.options.first().id))
        runCurrent()
        assertNull(repository.read().engine!!.currentEvent)
        assertEquals(EventStatus.COMPLETED, repository.read().engine!!.events.first().status)
        assertEquals(1, repository.read().story.decisions.size)
        assertNull(repository.requests.last().context)
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
        val retryCard = model.uiState.value
        model.onAction(DayAction.Later)
        runCurrent()
        assertEquals(1, exits)
        assertEquals(retryCard.copy(busy = true, retryRequired = false), model.uiState.value)
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
        assertTrue(model.uiState.value.actionNotice!!.startsWith("Рыжик проголодался."))
        assertEquals("Пора подкрепиться", model.uiState.value.title)
        assertTrue(model.uiState.value.body.isBlank())
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
        val hungerNotice = model.uiState.value.actionNotice
        assertTrue(hungerNotice!!.startsWith("Рыжик проголодался."))
        model.onAction(DayAction.ShowMeals)
        assertTrue(model.uiState.value.showMeals)
        repository.failure = IOException("Write unavailable")
        model.onAction(DayAction.Feed(session.catalog.meals.first { it.price > 0 }.id))
        runCurrent()
        assertEquals(hungerNotice, model.uiState.value.actionNotice)
        assertTrue(model.uiState.value.message!!.contains("Не удалось сохранить"))
        assertTrue(model.uiState.value.options.single().needsFood)
        repository.failure = null
        model.onAction(DayAction.Feed(session.catalog.meals.first { it.price > 0 }.id))
        runCurrent()
        assertFalse(model.uiState.value.showMeals)
        assertFalse(model.uiState.value.options.single().needsFood)
        assertNull(model.uiState.value.actionNotice)
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
        assertEquals(previous.economy.plan, morning.economy.plan)
        assertEquals(100L, morning.economy.unallocated)
        assertNotNull(morning.economy.planning)
        assertNull(session.advanceCommand(morning))
        assertNull(morning.engine!!.currentEvent)
        assertEquals(0, morning.engine!!.steps)
        assertTrue(exits.isEmpty())
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

    @Test fun telescopeWorkCanBeDeferredInBothCatalogVersionsWithoutLosingItsDeadline() = runTest(dispatcher) {
        for (id in listOf("figma-2163-43-v1", "figma-2163-43-v1:balance-v2")) {
            val (repository, model, session) = fixture(eventFirst = id)
            runCurrent()
            val before = repository.read()
            val offer = before.engine!!.deeds.single()
            assertEquals("Сделать позже", model.uiState.value.later)
            assertEquals("Успеть до конца сегодня", model.uiState.value.deedDeadline)
            model.onAction(DayAction.Later)
            runCurrent()
            val after = repository.read()
            assertEquals(listOf(offer), session.engine.availableDeeds(after))
            assertEquals(before.economy, after.economy)
            assertEquals(before.engine!!.energy, after.engine!!.energy)
            assertEquals(before.engine!!.steps, after.engine!!.steps)
            assertNull(after.engine!!.currentEvent)
        }
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

    @Test fun lostDeedStartReplyDoesNotOpenACompletedOrDifferentActiveOccurrenceOnRetry() = runTest(dispatcher) {
        for (startAnotherDeed in listOf(false, true)) {
            val (repository, model, session) = fixture(deedFirst = true)
            runCurrent()
            val games = mutableListOf<String>()
            val exits = mutableListOf<String?>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openGame.collect { games += it } }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
            repository.afterWrite = { throw IOException("Reply lost after deed start") }
            model.onAction(DayAction.Choose(model.uiState.value.options.single().id))
            runCurrent()
            val original = repository.attempted.last()
            val started = checkNotNull(repository.read().engine!!.currentEvent)
            assertEquals(EventStatus.ACTIVE, started.status)
            assertTrue(model.uiState.value.retryRequired)
            assertTrue(games.isEmpty())

            repository.afterWrite = {}
            val score = checkNotNull(DeedGameScore.fromComparison(PriceQuizState.create().copy(current = 5, correctAnswers = 3)))
            assertTrue(session.dispatch(EngineRequest("completed-elsewhere", repository.read().engine!!.revision,
                EngineCommand.CompleteDeed(started.id, score))) is EngineResult.Applied)
            assertNull(repository.read().engine!!.currentEvent)
            if (startAnotherDeed) {
                assertTrue(session.dispatch(EngineRequest("opened-elsewhere", repository.read().engine!!.revision,
                    EngineCommand.OpenNextEvent)) is EngineResult.Applied)
                val proposal = checkNotNull(repository.read().engine!!.currentEvent)
                assertTrue(session.dispatch(EngineRequest("started-elsewhere", repository.read().engine!!.revision,
                    EngineCommand.AcceptDeedProposal(proposal.id))) is EngineResult.Applied)
                assertEquals(EventStatus.ACTIVE, repository.read().engine!!.currentEvent!!.status)
                assertNotEquals(started.id, repository.read().engine!!.currentEvent!!.id)
            }
            runCurrent()
            val latest = repository.read()
            val writes = repository.writes

            model.onAction(DayAction.Retry)
            runCurrent()
            assertEquals(original, repository.attempted.last())
            assertEquals(latest, repository.read())
            assertEquals(writes, repository.writes)
            assertEquals(1, repository.requests.count { it.id == original.id })
            assertTrue(games.isEmpty())
            assertTrue(exits.isEmpty())
            assertFalse(model.uiState.value.busy)
            assertFalse(model.uiState.value.retryRequired)
            assertTrue(model.uiState.value.message.orEmpty().contains("Состояние дела уже изменилось"))
        }
    }

    @Test fun lostStoryStartReplyDoesNotOpenTheBoardAfterItsOccurrenceWasCompleted() = runTest(dispatcher) {
        val (repository, model, session) = fixture(storyMiniGame = true)
        runCurrent()
        val games = mutableListOf<StoryGameRequest>()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openStoryGame.collect { games += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        repository.afterWrite = { throw IOException("Reply lost after story start") }
        model.onAction(DayAction.Choose(model.uiState.value.options.single().id))
        runCurrent()
        val original = repository.attempted.last()
        val command = original.command as EngineCommand.StartStoryGame
        assertTrue(model.uiState.value.retryRequired)
        assertTrue(games.isEmpty())

        repository.afterWrite = {}
        val score = checkNotNull(DeedGameScore.fromPrecision(TargetStopState.create().copy(round = 5, hits = 2, lastHit = true)))
        assertTrue(session.dispatch(EngineRequest("story-completed-elsewhere", repository.read().engine!!.revision,
            EngineCommand.CompleteStoryGame(command.occurrenceId, command.choiceId, score))) is EngineResult.Applied)
        runCurrent()
        val latest = repository.read()
        assertNull(latest.engine!!.currentEvent)
        val writes = repository.writes

        model.onAction(DayAction.Retry)
        runCurrent()
        assertEquals(original, repository.attempted.last())
        assertEquals(latest, repository.read())
        assertEquals(writes, repository.writes)
        assertEquals(1, repository.requests.count { it.id == original.id })
        assertTrue(games.isEmpty())
        assertTrue(exits.isEmpty())
        assertFalse(model.uiState.value.busy)
        assertFalse(model.uiState.value.retryRequired)
        assertFalse(model.uiState.value.message.isNullOrBlank())
    }

    @Test fun purchaseOffersPassingByWithoutPausingAndWarnsAboutFoodMoney() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        repository.update { it.copy(economy = it.economy.copy(plan = BudgetPlan(27, 0, 0, 0),
            availableBalance = 27, savingsBalance = 0, unallocated = 0)) }
        runCurrent()
        assertEquals(listOf("Купить", "Пройти мимо"), model.uiState.value.options.map { it.label })
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
        assertTrue(model.uiState.value.actionNotice!!.contains("проголодался"))
        model.onAction(DayAction.ShowMeals)
        runCurrent()
        assertTrue(model.uiState.value.showMeals)
        model.onAction(DayAction.Feed("basic-v1"))
        runCurrent()
        assertTrue(model.uiState.value.options.all { it.enabled && !it.needsFood })
        assertNull(model.uiState.value.actionNotice)
        assertEquals(95L, repository.read().economy.balance)
        assertEquals(5, repository.read().engine!!.energy)
        assertEquals(3, repository.read().engine!!.steps)
        assertTrue(repository.read().story.decisions.isEmpty())
    }

    @Test fun openLegacyEventAndMealPromptUseTheLatestPersistedName() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2313-2-v1")
        runCurrent()
        repository.update { it.copy(pet = it.pet.copy(name = "Тоша"), engine = it.engine!!.copy(steps = 3)) }
        runCurrent()
        assertEquals("Тоша", model.uiState.value.petName)
        assertTrue(model.uiState.value.actionNotice!!.startsWith("Тоша проголодался."))
        assertTrue(model.uiState.value.body.startsWith("Тоша прислонился"))
        model.onAction(DayAction.ShowMeals)
        assertTrue(model.uiState.value.showMeals)
        assertFalse(model.uiState.value.body.contains("Рыжик"))
    }

    @Test fun paidChoiceShowsTheRealPaymentSourceAndKeepsSavingsSeparate() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        repository.update { it.copy(economy = EconomyState(BudgetPlan(35, 3, 0, 22), savingsBalance = 20)) }
        runCurrent()
        val paid = model.uiState.value.options.first()
        assertTrue(paid.enabled)
        val explanation = checkNotNull(paid.spending)
        assertTrue(explanation.contains("3 монеты из денег на желания"))
        assertTrue(explanation.contains("22 монеты из запаса"))
        assertEquals(20L, repository.read().economy.savingsBalance)
        assertNull(model.uiState.value.options.last().spending)
    }

    @Test fun bakeryCardHasTwoClearChoicesWithoutHiddenEvidenceAndPreservesBothOutcomes() = runTest(dispatcher) {
        val eventId = "figma-2654-2-purchase-v2"
        for (buy in listOf(true, false)) {
            val (repository, model, session) = fixture(eventFirst = eventId)
            repository.update { saved -> saved.copy(
                pet = saved.pet.copy(name = "Тоша"), selectedGoalId = session.catalog.goals.first().goalId,
                economy = EconomyState(BudgetPlan(6, 0, 0, 0), availableBalance = 6, savingsBalance = 30),
            ) }
            runCurrent()
            val shown = model.uiState.value
            assertEquals("Пекарня", shown.locationTitle)
            assertEquals(EventLayout.PURCHASE, shown.layout)
            assertEquals(R.drawable.prop_bakery_bun, shown.purchaseArtworkRes)
            assertEquals("Ароматная булочка", shown.title)
            assertEquals("Заменяет обычный приём пищи. Тоша будет доволен: она гораздо вкуснее обычного обеда.", shown.body)
            assertEquals(listOf("Купить", "Пройти мимо"), shown.options.map { it.label })
            assertTrue(shown.options.all { it.enabled && !it.needsFood })
            assertEquals("", shown.impact)
            assertEquals("", shown.effort)
            assertNull(shown.financialContext)
            assertNull(shown.message)
            assertNotNull(shown.options.first().spending)
            assertNull(shown.options.last().spending)

            val writes = repository.writes
            model.onAction(DayAction.FinancialContextPresented("day:${repository.read().engine!!.currentEvent!!.id}:${repository.read().engine!!.revision}"))
            assertEquals(writes, repository.writes)
            model.onAction(DayAction.Choose("$eventId:${if (buy) "buy" else "pass"}"))
            runCurrent()

            val after = repository.read()
            assertEquals(if (buy) 0L else 6L, after.economy.availableBalance)
            assertEquals(30L, after.economy.savingsBalance)
            assertEquals(buy, after.engine!!.ateToday)
            assertEquals(if (buy) PetVisualState.HAPPY else PetVisualState.NORMAL, after.pet.visualState)
            val request = repository.requests.last()
            assertNull((request.command as EngineCommand.CompleteEvent).priorityId)
            assertNull(request.context)
        }
    }

    @Test fun purchaseCardsKeepTheirArtworkAndOnlyOnePaymentExplanation() = runTest(dispatcher) {
        val expected = listOf(
            "figma-2164-2-v1" to R.drawable.gear_explorer_hat,
            "figma-2654-50-purchase-v2" to R.drawable.prop_fair_explorer_hat,
            "figma-2654-98-purchase-v2" to R.drawable.prop_fair_ring_toss,
            "figma-56-55-purchase-v2" to R.drawable.gear_pilot_goggles,
            "figma-56-49-purchase-v2" to R.drawable.gear_route_patch,
            "figma-2654-146-purchase-v2" to R.drawable.prop_fair_compass_keychain,
            "figma-56-64-purchase-v2" to R.drawable.gear_binoculars,
            "figma-2654-194-purchase-v2" to R.drawable.prop_fair_toy_boat,
        )
        expected.forEach { (event, artwork) ->
            val (repository, model, session) = fixture(eventFirst = event)
            repository.update { it.copy(selectedGoalId = session.catalog.goals.first().goalId) }
            runCurrent()
            val shown = model.uiState.value
            assertEquals(event, artwork, shown.purchaseArtworkRes)
            assertEquals("Ярмарка", shown.locationTitle)
            assertEquals(EventLayout.PURCHASE, shown.layout)
            assertNull(shown.financialContext)
            assertTrue(shown.impact.isEmpty())
            assertFalse(shown.title.any(Char::isDigit))
            assertFalse(shown.body.any(Char::isDigit))
            assertEquals(if (event == "figma-2654-98-purchase-v2") "Сыграть" else "Купить", shown.options.first().label)
            assertEquals("Пройти мимо", shown.options.last().label)
            assertEquals(1, shown.options.count { it.spending != null })
            assertEquals(2, shown.options.size)
            assertNull(shown.message)
        }
    }

    @Test fun missingIllustrationDoesNotChangeThePurchaseLayoutOrItsActions() = runTest(dispatcher) {
        val id = "figma-2654-98-purchase-v2"
        val (repository, model) = fixture(eventFirst = id, catalogTransform = { catalog ->
            val card = catalog.cards.getValue(id)
            catalog.copy(cards = catalog.cards + (id to card.copy(presentation = card.presentation.copy(
                media = card.presentation.media.copy(artworkKey = "unavailable.illustration")))))
        })
        runCurrent()
        val shown = model.uiState.value
        assertEquals(EventLayout.PURCHASE, shown.layout)
        assertNull(shown.purchaseArtworkRes)
        assertEquals("Кольцеброс", shown.title)
        assertEquals("Ярмарка", shown.locationTitle)
        assertEquals(listOf("Сыграть", "Пройти мимо"), shown.options.map { it.label })
        assertEquals(0, repository.writes)
    }

    @Test fun ordinaryRefusalWithoutPresentedNumbersDoesNotClaimFinancialEvidenceOrPriority() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        runCurrent()
        assertNotEquals(EventLayout.INTRODUCTION, model.uiState.value.layout)
        assertNull(model.uiState.value.financialContext)
        model.onAction(DayAction.Choose("figma-2164-2-v1:pass"))
        runCurrent()
        val request = repository.requests.last()
        assertNull((request.command as EngineCommand.CompleteEvent).priorityId)
        assertNull(request.context)
    }

    @Test fun passingByWithASelectedGoalDoesNotInventFinancialEvidenceOrAnExplicitSavingIntent() = runTest(dispatcher) {
        val (repository, model, session) = fixture(eventFirst = "figma-2164-2-v1")
        val goal = session.catalog.goals.first().goalId
        repository.update { it.copy(selectedGoalId = goal) }
        runCurrent()
        assertNull(model.uiState.value.financialContext)
        model.onAction(DayAction.FinancialContextPresented("day:${repository.read().engine!!.currentEvent!!.id}:${repository.read().engine!!.revision}"))
        model.onAction(DayAction.Choose("figma-2164-2-v1:pass"))
        runCurrent()
        val request = repository.requests.last()
        assertNull((request.command as EngineCommand.CompleteEvent).priorityId)
        assertNull(request.context)
    }

    @Test fun earningIntentIsExplicitInTheLabelAndCommand() = runTest(dispatcher) {
        val (repository, model, session) = fixture(deedFirst = true)
        val goal = session.catalog.goals.first().goalId
        repository.update { it.copy(selectedGoalId = goal) }
        runCurrent()
        assertEquals("Заработать на цель", model.uiState.value.options.single().label)
        model.onAction(DayAction.Choose(model.uiState.value.options.single().id))
        runCurrent()
        assertEquals(goal, (repository.requests.last().command as EngineCommand.AcceptDeedProposal).priorityId)
    }

    @Test fun cargoRecordHasNoSkipOrBudgetSummaryAndCompletesWithoutClaimingShownMoney() = runTest(dispatcher) {
        val id = "campaign-choice-v1:G1.04"
        // Historical cleaning and both current alternatives unlock the same next clue.
        for (plateChoice in listOf("campaign-choice-v1:G1.03:continue",
            "campaign-choice-v2:G1.03:continue", "campaign-choice-v2:G1.03:pay")) {
            val (repository, model) = fixture(eventFirst = id, priorChoices = listOf(
                "campaign-choice-v1:G1.01:continue", "figma-2270-2-v1:complete",
                "campaign-choice-v1:G1.02:continue", plateChoice))
            runCurrent()
            assertEquals(listOf("Изучить запись"), model.uiState.value.options.map { it.label })
            assertEquals("Вернуться позже", model.uiState.value.later)
            assertNull(model.uiState.value.financialContext)
            val before = repository.read()
            model.onAction(DayAction.Choose("$id:skip"))
            runCurrent()
            assertEquals(before, repository.read())
            model.onAction(DayAction.Choose("$id:continue"))
            runCurrent()
            assertNull(repository.requests.last().context)
            assertEquals("$id:continue", repository.read().story.decisions.last().choiceId)
        }
    }

    @Test fun eventChoicesDoNotInventFinancialContextWhenTheSummaryIsNotShown() = runTest(dispatcher) {
        for (price in listOf(0L, 4L)) {
            val (repository, model) = fixture(catalogTransform = { catalog ->
                val original = catalog.content.choices.single { it.eventId == catalog.introductionId }
                catalog.copy(content = catalog.content.copy(choices = catalog.content.choices + original.copy(
                    id = "${original.id}-alternative", position = 1, text = "Другой путь", moneyDelta = -price)))
            })
            runCurrent()
            assertEquals(2, model.uiState.value.options.size)
            assertNull(model.uiState.value.financialContext)
            model.onAction(DayAction.Choose(model.uiState.value.options.last().id))
            runCurrent()
            assertNull(repository.requests.last().context)
        }
    }

    @Test fun practicalStoryChoiceOpensItsBoardWithoutCompletingTheActionOrLeavingAsSuccess() = runTest(dispatcher) {
        val (repository, model) = fixture(storyMiniGame = true)
        runCurrent()
        val before = repository.read()
        val games = mutableListOf<StoryGameRequest>()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.openStoryGame.collect { games += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        val option = model.uiState.value.options.single()
        assertTrue(option.enabled)
        model.onAction(DayAction.Choose(option.id))
        runCurrent()
        assertEquals(listOf(StoryGameRequest(before.engine!!.currentEvent!!.id, option.id)), games)
        assertTrue(repository.requests.last().command is EngineCommand.StartStoryGame)
        assertEquals(before.story.decisions, repository.read().story.decisions)
        assertEquals(before.economy, repository.read().economy)
        assertEquals(before.engine!!.steps, repository.read().engine!!.steps)
        assertTrue(exits.isEmpty())
    }

    @Test fun resourcePriorityRequiresALiveOfferAndAnExplicitChoiceAndResetsOnRevision() = runTest(dispatcher) {
        val (repository, model, session) = fixture(eventFirst = "figma-2313-2-v1")
        runCurrent()
        assertNull(model.uiState.value.resourcePriority)
        val deed = session.catalog.deedPool.first { session.catalog.policies.getValue(it).energyCost == 1 }
        repository.update { saved -> saved.copy(engine = saved.engine!!.copy(
            energy = 2, ateToday = true,
            deeds = listOf(DeedOffer("urgent-offer", deed, saved.engine!!.day)))) }
        runCurrent()
        assertEquals("urgent-offer", model.uiState.value.resourcePriority?.offerId)
        assertFalse(checkNotNull(model.uiState.value.resourcePriority).selected)
        model.onAction(DayAction.SetResourcePriority("urgent-offer", true))
        assertTrue(checkNotNull(model.uiState.value.resourcePriority).selected)
        repository.update { saved -> saved.copy(engine = saved.engine!!.copy(revision = saved.engine!!.revision + 1)) }
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value.resourcePriority).selected)
        model.onAction(DayAction.SetResourcePriority("urgent-offer", true))
        model.onAction(DayAction.Choose("figma-2313-2-v1:pay"))
        runCurrent()
        assertEquals("urgent-offer", (repository.requests.last().command as EngineCommand.CompleteEvent).resourcePriorityOfferId)
    }

    @Test fun failedChoiceRetriesTheSameRequestAndDoesNotAcceptAnotherIntent() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        runCurrent()
        val before = repository.read()
        val buy = DayAction.Choose("figma-2164-2-v1:buy")
        repository.failure = IOException("Write unavailable")
        model.onAction(buy)
        runCurrent()
        val original = repository.attempted.last()
        assertTrue(model.uiState.value.retryRequired)
        assertEquals(before, repository.read())
        val attempts = repository.attempted.size
        model.onAction(DayAction.Choose("figma-2164-2-v1:pass"))
        model.onAction(DayAction.Later)
        runCurrent()
        assertEquals(attempts, repository.attempted.size)
        repository.failure = null
        model.onAction(DayAction.Retry)
        runCurrent()
        assertEquals(original, repository.attempted.last())
        assertEquals(listOf(original, original), repository.attempted.takeLast(2))
        assertEquals(1, repository.requests.count { it.id == original.id })
        assertEquals(before.story.decisions.size + 1, repository.read().story.decisions.size)
    }

    @Test fun choiceCommittedBeforeReplyFailureKeepsItsCardAndConfirmsExactlyOnceOnRetry() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        runCurrent()
        val before = repository.read()
        val card = model.uiState.value
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        repository.afterWrite = { throw IOException("Reply lost after commit") }
        model.onAction(DayAction.Choose("figma-2164-2-v1:buy"))
        runCurrent()
        val committed = repository.read()
        val original = repository.attempted.last()
        assertEquals(before.story.decisions.size + 1, committed.story.decisions.size)
        assertTrue(committed.economy.balance < before.economy.balance)
        assertTrue(exits.isEmpty())
        assertTrue(model.uiState.value.retryRequired)
        assertEquals(card.title, model.uiState.value.title)
        assertEquals(card.options, model.uiState.value.options)
        repository.afterWrite = {}
        model.onAction(DayAction.Retry)
        runCurrent()
        assertEquals(original, repository.attempted.last())
        assertEquals(committed, repository.read())
        assertEquals(1, repository.writes)
        assertEquals(1, exits.size)
        assertNotNull(exits.single())
        model.onAction(DayAction.Retry)
        runCurrent()
        assertEquals(1, exits.size)
        assertEquals(1, repository.writes)
    }

    @Test fun recoveredChoiceFeedbackUsesItsReceiptInsteadOfLaterUnrelatedMoney() = runTest(dispatcher) {
        val (repository, model) = fixture(eventFirst = "figma-2164-2-v1")
        runCurrent()
        val before = repository.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.exit.collect { exits += it } }
        repository.afterWrite = { throw IOException("Reply lost after commit") }
        model.onAction(DayAction.Choose("figma-2164-2-v1:buy"))
        runCurrent()
        val purchased = repository.read()
        val spent = before.economy.balance - purchased.economy.balance
        assertTrue(spent > 0)
        val original = repository.attempted.last()
        repository.afterWrite = {}
        repository.update { game -> game.copy(
            economy = game.economy.copy(availableBalance = game.economy.availableBalance + 30,
                plan = game.economy.plan.copy(reserve = game.economy.plan.reserve + 30)),
            engine = game.engine!!.copy(revision = game.engine!!.revision + 1)) }
        runCurrent()
        val changed = repository.read()
        val writesBeforeRetry = repository.writes
        model.onAction(DayAction.Retry)
        runCurrent()
        assertEquals(original, repository.attempted.last())
        assertEquals(changed, repository.read())
        assertEquals(writesBeforeRetry, repository.writes)
        assertEquals(1, exits.size)
        assertTrue(exits.single().orEmpty().contains("Потратили $spent монет"))
        assertFalse(exits.single().orEmpty().contains("Получили"))
        assertEquals(1, repository.requests.count { it.id == original.id })
    }

    @Test fun failedDayActionKeepsItsRevisionUntilExplicitStaleRejection() = runTest(dispatcher) {
        val (repository, model) = fixture()
        runCurrent()
        val before = repository.read()
        val choice = model.uiState.value.options.single().id
        repository.failure = IOException("Write unavailable")
        model.onAction(DayAction.Choose(choice))
        runCurrent()
        val original = repository.attempted.last()
        repository.failure = null
        repository.update { game -> game.copy(engine = game.engine!!.copy(revision = game.engine!!.revision + 1)) }
        runCurrent()
        val changed = repository.read()
        model.onAction(DayAction.Retry)
        runCurrent()
        assertEquals(original, repository.attempted.last())
        assertEquals(changed, repository.read())
        assertEquals(before.story.decisions, changed.story.decisions)
        assertFalse(model.uiState.value.retryRequired)
        assertTrue(model.uiState.value.message!!.contains("Игра уже изменилась"))
        // Only a new explicit click can bind a new request to the refreshed revision.
        model.onAction(DayAction.Choose(choice))
        runCurrent()
        val fresh = repository.attempted.last()
        assertNotEquals(original.id, fresh.id)
        assertEquals(changed.engine!!.revision, fresh.expectedRevision)
        assertEquals(before.story.decisions.size + 1, repository.read().story.decisions.size)
    }

    private suspend fun fixture(deedFirst: Boolean = false, finishedDay: Int? = null,
        eventFirst: String? = null, storyMiniGame: Boolean = false, priorChoices: List<String> = emptyList(),
        catalogTransform: (GameCatalog) -> GameCatalog = { it }): Triple<DayRepository, DayViewModel, GameSession> {
        val original = catalogTransform(bundledGameCatalog())
        val catalog = if (storyMiniGame) original.copy(policies = original.policies +
            (original.introductionId to original.policies.getValue(original.introductionId).copy(
                choiceGameKinds = mapOf(original.content.choices.single { it.eventId == original.introductionId }.id to
                    ru.nksk.lctapp.domain.minigame.DeedGameKind.PRECISION)))) else original
        val initial = createInitialGameState().let { it.copy(economy = EconomyState(plan = BudgetPlan(35, 20, 20, 25),
            unallocated = 0, planning = null, availableBalance = 100, savingsBalance = 0)) }.let { state ->
            state.copy(
                selectedGoalId = catalog.goals.first().goalId.takeIf { !deedFirst && (eventFirst == null || priorChoices.isNotEmpty()) },
                story = state.story.copy(decisions = priorChoices.mapIndexed { index, id -> StoryDecision("prior-$index", id) }),
            )
        }
        val repository = DayRepository(initial)
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
    val requests = mutableListOf<EngineRequest>()
    val attempted = mutableListOf<EngineRequest>()
    private data class Receipt(val request: EngineRequest, val context: DecisionContext?, val fingerprint: String?)
    private val receipts = mutableMapOf<String, Receipt>()
    private val history = mutableListOf(AuditEntry("initial", 1, "run", AuditType.INITIALIZED, after = initial))
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun readHistory() = history.toList()
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
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
        // A receipt and aggregate become durable together, before a response can be lost.
        receipts[request.id] = identity
        requests += request
        history += AuditEntry("command:run:${request.id}", history.last().sequence + 1, "run", AuditType.COMMAND,
            request = request, context = context, before = before, after = next, contentFingerprint = contentFingerprint)
        state.value = next
        writes += 1
        afterWrite()
        return next
    }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        failure?.let { throw it }
        val next = transform(state.value)
        state.value = next
        writes++
        afterWrite()
        return next
    }
}
