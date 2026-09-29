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
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.QuizQuestion
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.HistorySourceGuard
import ru.nksk.lctapp.domain.history.HistoryFactLookup
import ru.nksk.lctapp.domain.demo.DemoPreferences
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.domain.demo.DisabledDemoPreferencesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class DeedGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun ordinarySessionsNeverOfferOrSubmitASkip() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read()
        assertFalse(f.model.uiState.value.canSkipGame)
        assertFalse(f.model.uiState.value.demoMode)
        f.model.skipGame()
        runCurrent()
        assertEquals(before, f.repo.read())
        assertEquals(0, f.repo.writes)
    }

    @Test fun demoSkipsStoryBoardWithoutPlayingOrReadingHistoricalAnswerEvidence() = runTest(dispatcher) {
        val f = fixture(storyGame = true, demoPreferences = MutableDemoPreferences(true))
        runCurrent()
        assertTrue(f.model.uiState.value.canSkipGame)
        f.repo.forbidFullHistoryRead = true
        f.repo.beforeFactRead = { error("Skipping must not look up quiz answers") }
        val before = f.repo.read()
        f.model.skipGame()
        runCurrent()
        val request = f.repo.committed.last()
        assertTrue(request.demoMode)
        assertTrue(request.command is EngineCommand.SkipMiniGame)
        val skipped = request.command as EngineCommand.SkipMiniGame
        assertEquals(f.id, skipped.occurrenceId)
        assertNotNull(skipped.choiceId)
        assertEquals(1, f.repo.writes)
        assertEquals(before.economy, f.repo.read().economy)
        assertEquals(before.story.decisions.size + 1, f.repo.read().story.decisions.size)
        assertEquals(EventStatus.COMPLETED, f.repo.read().engine!!.events.single { it.id == f.id }.status)
    }

    @Test fun lostSkipReplyKeepsOneDemoIntentAfterThePreferenceIsDisabled() = runTest(dispatcher) {
        val preferences = MutableDemoPreferences(true)
        val f = fixture(demoPreferences = preferences)
        runCurrent()
        val before = f.repo.read()
        val maximum = f.model.uiState.value.presentation!!.maximumReward
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.repo.afterWrite = { throw IOException("Committed reply was lost") }
        f.model.skipGame()
        f.model.skipGame()
        runCurrent()
        val original = f.repo.attempted.last()
        val committed = f.repo.read()
        assertTrue(original.demoMode)
        assertTrue(original.command is EngineCommand.SkipMiniGame)
        assertEquals(before.economy.balance + maximum, committed.economy.balance)
        assertTrue(f.model.uiState.value.canRetry)
        assertFalse(f.model.uiState.value.canSkipGame)
        assertTrue(exits.isEmpty())
        preferences.setDemoModeEnabled(false)
        f.repo.afterWrite = {}
        runCurrent()
        f.model.retry()
        runCurrent()
        assertEquals(original, f.repo.attempted.last())
        assertEquals(committed, f.repo.read())
        assertEquals(1, f.repo.writes)
        assertEquals(listOf("Игра пропущена · режим бога"), exits)
    }

    @Test fun activeDeedKeepsItsOwnLocationAudioAndOccurrenceIdentity() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val occurrence = f.repo.read().engine!!.events.single { it.id == f.id }
        assertEquals(f.id, f.model.uiState.value.audioOccurrenceId)
        assertEquals("ambient.port", f.model.uiState.value.eventMedia.ambientCueKey)
        assertEquals(f.session.catalog.cards.getValue(occurrence.eventId).presentation.media,
            f.model.uiState.value.eventMedia)
    }

    @Test fun restoredJournalAndCrateRepairsExplainTheirMemoryBoardsWithoutChangingTheSave() = runTest(dispatcher) {
        val catalog = bundledGameCatalog()
        for ((eventId, contextWord) in mapOf("figma-2326-112-v2" to "страницы", "figma-2326-352-v2" to "ящика")) {
            val occurrence = EventOccurrence("saved-$eventId", eventId, EventOrigin.SCHEDULE, EventStatus.ACTIVE)
            val initial = createInitialGameState().copy(
                economy = EconomyState(BudgetPlan(35, 0, 0, 0)),
                engine = EngineState(catalog.rules.id, 0, 1, DayPhase.RUNNING, 0, 5, true, null, 35,
                    listOf(occurrence), emptyList()),
            )
            val repo = DeedRepository(initial)
            val session = GameSession(repo, object : StoryContentRepository {
                override suspend fun read() = catalog.content
                override suspend fun install(content: StoryContent) = Unit
            }, catalog, initial)
            session.prepare()
            val before = repo.read()
            val model = DeedGameViewModel(session)
            store.put(eventId, model)
            model.load(occurrence.id, "$eventId:work")
            runCurrent()

            val state = model.uiState.value
            val presentation = checkNotNull(state.presentation)
            assertEquals(DeedGameType.MEMORY, state.type)
            assertTrue(presentation.instructions!!.contains(contextWord))
            assertTrue(presentation.instructions.contains("по две карточки"))
            assertFalse(presentation.instructions.contains("маркер"))
            assertEquals(before, repo.read())
            assertTrue(repo.attempted.isEmpty())
        }
    }

    @Test fun completedGameWaitsForSavingAndCannotPayTwiceOrShowAnotherBoard() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read()
        val canReturn = CompletableDeferred<Unit>()
        f.repo.afterWrite = { canReturn.await() }
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }

        f.model.finishComparison(completedQuiz())
        f.model.finishComparison(completedQuiz())
        f.model.leave()
        runCurrent()
        assertEquals(before.economy.balance + 4, f.repo.read().economy.balance)
        assertTrue(exits.isEmpty())
        assertTrue(f.model.uiState.value.busy)
        assertFalse(f.model.uiState.value.presentation!!.canPlay)
        canReturn.complete(Unit)
        runCurrent()
        assertEquals(listOf("Дело выполнено! Награда: 4 монеты"), exits)
        assertEquals(1, f.repo.writes)

        val restored = DeedGameViewModel(f.session)
        store.put("restored", restored)
        val restoredExits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.exit.collect { restoredExits += it } }
        restored.load(f.id)
        runCurrent()
        assertEquals(listOf<String?>(null), restoredExits)
        assertEquals(1, f.repo.writes)
        assertEquals(f.repo.read(), f.session.read())
    }

    @Test fun failedCompletionKeepsTheResultForExplicitRetry() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        val before = f.repo.read()
        f.repo.failure = IOException("Unavailable")
        f.model.finishComparison(completedQuiz())
        runCurrent()
        assertEquals(before, f.repo.read())
        assertTrue(exits.isEmpty())
        val original = f.repo.attempted.last()
        assertNotNull(f.model.uiState.value.message)
        assertFalse(f.model.uiState.value.presentation!!.canPlay)
        f.repo.failure = null
        f.model.retry()
        runCurrent()
        assertEquals(original, f.repo.attempted.last())
        assertEquals(1, f.repo.committed.count { it.id == original.id })
        assertEquals(before.economy.balance + 4, f.repo.read().economy.balance)
        assertEquals(1, f.repo.writes)
        assertEquals(listOf("Дело выполнено! Награда: 4 монеты"), exits)
    }

    @Test fun leavingAnIncompleteBoardPreservesTheOfferDeadlineWithoutPaymentOrEffort() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        val before = f.repo.read()
        val offer = before.engine!!.deeds.single()
        f.model.finishComparison(PriceQuizState.create())
        runCurrent()
        assertEquals(0, f.repo.writes)
        f.model.leave()
        runCurrent()
        val paused = f.repo.read()
        assertEquals(listOf<String?>(null), exits)
        assertEquals(EventStatus.PAUSED, paused.engine!!.events.single { it.id == f.id }.status)
        assertEquals(offer, paused.engine!!.deeds.single())
        assertEquals(before.economy, paused.economy)
        assertEquals(before.engine!!.energy, paused.engine!!.energy)
        assertEquals(before.engine!!.steps, paused.engine!!.steps)
        assertTrue(f.session.dispatch(EngineRequest("restart", paused.engine!!.revision,
            EngineCommand.StartDeed(offer.id))) is EngineResult.Applied)
        assertEquals(f.id, f.repo.read().engine!!.currentEvent!!.id)
        assertEquals(offer.expiresDay, f.repo.read().engine!!.deeds.single().expiresDay)
    }

    private fun completedQuiz() = PriceQuizState.create().copy(current = 5, correctAnswers = 3)

    @Test fun aStoryBoardCompletesItsChoiceWithoutPretendingToBePaidWork() = runTest(dispatcher) {
        val f = fixture(storyGame = true)
        runCurrent()
        val before = f.repo.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        assertTrue(f.model.uiState.value.presentation!!.storyAction)
        assertEquals(DeedGameType.PRECISION, f.model.uiState.value.type)
        f.model.finishPrecision(ru.nksk.lctapp.domain.minigame.TargetStopState.create())
        runCurrent()
        assertEquals(0, f.repo.writes)
        f.model.finishPrecision(ru.nksk.lctapp.domain.minigame.TargetStopState.create().copy(round = 5, hits = 2, lastHit = true))
        runCurrent()
        assertEquals(1, f.repo.writes)
        assertEquals(before.economy, f.repo.read().economy)
        assertEquals(before.story.decisions.size + 1, f.repo.read().story.decisions.size)
        assertEquals(EventStatus.COMPLETED, f.repo.read().engine!!.events.single { it.id == f.id }.status)
        assertEquals(1, exits.size)
        assertFalse(exits.single().orEmpty().contains("Награда"))
    }

    @Test fun backDuringInitialLoadingWaitsForTheSnapshotAndPersistsPause() = runTest(dispatcher) {
        val f = fixture()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.model.leave() // No observer turn has supplied latest yet.
        assertTrue(exits.isEmpty())
        runCurrent()
        assertEquals(EventStatus.PAUSED, f.repo.read().engine!!.events.single { it.id == f.id }.status)
        assertEquals(listOf<String?>(null), exits)
        assertTrue(f.repo.read().story.decisions.isEmpty())
    }

    @Test fun noCorrectAnswersStillCreditAndConfirmOneCoin() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read().economy
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.model.finishComparison(completedQuiz().copy(correctAnswers = 0))
        runCurrent()
        assertEquals(listOf("Дело выполнено! Награда: 1 монета"), exits)
        assertTrue(f.repo.read().engine!!.deeds.single().completed)
        assertEquals(before.availableBalance + 1, f.repo.read().economy.availableBalance)
        assertEquals(before.plan.reserve + 1, f.repo.read().economy.plan.reserve)
        f.model.finishComparison(completedQuiz().copy(correctAnswers = 0))
        runCurrent()
        assertEquals(1, f.repo.writes)
        assertEquals(before.availableBalance + 1, f.repo.read().economy.availableBalance)
    }

    @Test fun actualComparisonAnswersAreDurableBeforePayoutAndAreNotDuplicated() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        f.repo.beforeFacts = { gate.await() }
        val evidence = PriceQuizEvidence("series", (0 until 5).map { index ->
            PriceQuizAnswerEvidence(index, 80, 20, index < 3, true, index == 4)
        })
        val game = PriceQuizState(List(5) { QuizQuestion(80, 20) }, current = 5, correctAnswers = 3)
        f.model.finishComparison(game, evidence)
        runCurrent()
        assertTrue(f.model.uiState.value.busy)
        assertEquals(0, f.repo.writes)
        assertTrue(f.repo.readHistory().flatMap { it.facts }.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, f.repo.writes)
        val facts = f.repo.readHistory().flatMap { it.facts }
        assertEquals(5, facts.size)
        val observation = SkillEvaluator().evaluate(facts).single()
        assertEquals(SkillId.COMPARE_AMOUNTS, observation.skill)
        assertEquals(3L, observation.measures["correctFirstAnswers"])
        f.model.finishComparison(game, evidence)
        runCurrent()
        assertEquals(5, f.repo.readHistory().flatMap { it.facts }.size)
        assertEquals(1, f.repo.writes)
    }

    @Test fun newAnswerDuringFactWriteUsesOnlyIndexedLookupsAndRetriesWithoutDuplicates() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read()
        f.repo.forbidFullHistoryRead = true
        val gate = CompletableDeferred<Unit>()
        f.repo.beforeFacts = { gate.await() }
        val first = PriceQuizEvidence("bounded-series", listOf(PriceQuizAnswerEvidence(0, 80, 20, true, true, false)))
        val second = first.copy(answers = first.answers + PriceQuizAnswerEvidence(1, 10, 60, false, true, false))
        f.model.recordComparisonAnswers(first)
        runCurrent()
        f.model.recordComparisonAnswers(second)
        runCurrent()
        assertTrue(f.repo.recordedFacts.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertEquals(PriceQuizEvidenceMapper.eventIds(second, f.id), f.repo.recordedFacts.map { it.eventId }.toSet())
        assertEquals(2, f.repo.recordedFacts.size)
        assertTrue(f.repo.factReadRequests.all { it.size <= 2 && it.all(PriceQuizEvidenceMapper.eventIds(second, f.id)::contains) })
        assertEquals(before, f.repo.read())
        assertNull(f.model.uiState.value.message)
        f.model.recordComparisonAnswers(second)
        runCurrent()
        assertEquals(2, f.repo.recordedFacts.size)
        assertEquals(0, f.repo.writes)
        assertNull(f.model.uiState.value.message)
    }

    @Test fun newAnswerDuringAnAlreadySavedAnswerLookupIsNotLost() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        f.repo.forbidFullHistoryRead = true
        val first = PriceQuizEvidence("lookup-series", listOf(PriceQuizAnswerEvidence(0, 80, 20, true, true, false)))
        f.model.recordComparisonAnswers(first)
        runCurrent()
        assertEquals(1, f.repo.recordedFacts.size)
        val gate = CompletableDeferred<Unit>()
        f.repo.beforeFactRead = { gate.await() }
        f.model.recordComparisonAnswers(first)
        runCurrent()
        val second = first.copy(answers = first.answers + PriceQuizAnswerEvidence(1, 10, 60, false, true, false))
        f.model.recordComparisonAnswers(second)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, f.repo.recordedFacts.size)
        assertEquals(PriceQuizEvidenceMapper.eventIds(second, f.id), f.repo.recordedFacts.map { it.eventId }.toSet())
        assertNull(f.model.uiState.value.message)

        f.model.recordComparisonAnswers(second.copy(answers = second.answers.map { it.copy(pickedLeft = !it.pickedLeft) }))
        runCurrent()
        assertEquals(2, f.repo.recordedFacts.size)
        assertTrue(f.model.uiState.value.canRetry)
        assertNotNull(f.model.uiState.value.message)
    }

    @Test fun storyCompletionRetryRetainsTheOriginalFinancialExposureEvidence() = runTest(dispatcher) {
        val context = DecisionContext(presentationId = "shown-before-story-work",
            informationPresented = true, complete = true)
        val f = fixture(storyGame = true, storyContext = context)
        runCurrent()
        f.repo.forbidFullHistoryRead = true
        f.repo.failure = IOException("Write unavailable")
        f.model.finishPrecision(ru.nksk.lctapp.domain.minigame.TargetStopState.create().copy(round = 5, hits = 2, lastHit = true))
        runCurrent()
        val original = f.repo.attempted.last()
        assertTrue(original.command is EngineCommand.CompleteStoryGame)
        assertEquals(context, original.context)
        f.repo.failure = null
        f.model.retry()
        runCurrent()
        assertEquals(original, f.repo.attempted.last())
        assertEquals(context, f.repo.committed.last().context)
        assertEquals(1, f.repo.committed.count { it.id == original.id })
        assertEquals(1, f.repo.latestCommandReads)
    }

    @Test fun payoutCommittedBeforeReplyFailureSurvivesObservationAndRetriesExactlyOnce() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.repo.afterWrite = { throw IOException("Reply lost after commit") }
        f.model.finishComparison(completedQuiz())
        runCurrent()
        val committed = f.repo.read()
        val original = f.repo.attempted.last()
        assertEquals(before.economy.balance + 4, committed.economy.balance)
        assertTrue(committed.engine!!.deeds.single().completed)
        assertTrue(exits.isEmpty())
        assertTrue(f.model.uiState.value.canRetry)
        assertFalse(f.model.uiState.value.busy)
        assertFalse(f.model.uiState.value.presentation!!.canPlay)
        f.repo.afterWrite = {}
        f.model.retry()
        runCurrent()
        assertEquals(original, f.repo.attempted.last())
        assertEquals(committed, f.repo.read())
        assertEquals(1, f.repo.writes)
        assertEquals(listOf("Дело выполнено! Награда: 4 монеты"), exits)
        f.model.retry()
        f.model.finishComparison(completedQuiz())
        runCurrent()
        assertEquals(1, f.repo.writes)
        assertEquals(1, exits.size)
    }

    @Test fun recoveredPayoutFeedbackExcludesMoneyReceivedAfterItsCommit() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.repo.afterWrite = { throw IOException("Reply lost after commit") }
        f.model.finishComparison(completedQuiz())
        runCurrent()
        val original = f.repo.attempted.last()
        f.repo.afterWrite = {}
        f.repo.update { game -> game.copy(
            economy = game.economy.copy(availableBalance = game.economy.availableBalance + 30,
                plan = game.economy.plan.copy(reserve = game.economy.plan.reserve + 30)),
            engine = game.engine!!.copy(revision = game.engine!!.revision + 1)) }
        runCurrent()
        val changed = f.repo.read()
        val writesBeforeRetry = f.repo.writes
        f.repo.forbidFullHistoryRead = true
        assertTrue(exits.isEmpty())
        assertTrue(f.model.uiState.value.canRetry)
        f.model.retry()
        runCurrent()
        assertEquals(original, f.repo.attempted.last())
        assertEquals(changed, f.repo.read())
        assertEquals(writesBeforeRetry, f.repo.writes)
        assertEquals(listOf("Дело выполнено! Награда: 4 монеты"), exits)
        assertEquals(1, f.repo.committed.count { it.id == original.id })
        assertEquals(listOf(original.id), f.repo.commandReceiptReads)
    }

    @Test fun failedDeedCompletionKeepsItsRequestUntilStaleRejectionThenExplicitRetrySavesTheOriginalScore() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val before = f.repo.read()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.repo.failure = IOException("Write unavailable")
        f.model.finishComparison(completedQuiz())
        runCurrent()
        val original = f.repo.attempted.last()
        f.repo.failure = null
        f.repo.update { game -> game.copy(engine = game.engine!!.copy(revision = game.engine!!.revision + 1)) }
        runCurrent()
        val changed = f.repo.read()
        f.model.retry()
        runCurrent()
        assertEquals(original, f.repo.attempted.last())
        assertEquals(changed, f.repo.read())
        assertEquals(before.economy, changed.economy)
        assertFalse(changed.engine!!.deeds.single().completed)
        assertTrue(exits.isEmpty())
        assertTrue(f.model.uiState.value.canRetry)
        assertFalse(f.model.uiState.value.busy)
        assertFalse(f.model.uiState.value.presentation!!.canPlay)
        assertTrue(f.model.uiState.value.message!!.contains("Повторить"))
        assertEquals(0, f.repo.committed.count { it.id == original.id })

        // A recomposed board must not replace the rejected result or resubmit it automatically.
        val attempts = f.repo.attempted.size
        f.model.finishComparison(completedQuiz().copy(correctAnswers = 5))
        runCurrent()
        assertEquals(attempts, f.repo.attempted.size)
        assertEquals(changed, f.repo.read())
        assertTrue(exits.isEmpty())

        val writes = f.repo.writes
        f.model.retry()
        runCurrent()
        val fresh = f.repo.attempted.last()
        assertNotEquals(original.id, fresh.id)
        assertEquals(changed.engine!!.revision, fresh.expectedRevision)
        assertEquals(original.command, fresh.command)
        assertEquals(original.context, fresh.context)
        assertEquals(listOf(original, original, fresh), f.repo.attempted.takeLast(3))
        assertEquals(writes + 1, f.repo.writes)
        assertEquals(1, f.repo.committed.count { it.command is EngineCommand.CompleteDeed })
        assertTrue(f.repo.read().engine!!.deeds.single().completed)
        assertEquals(before.economy.balance + 4, f.repo.read().economy.balance)
        assertEquals(listOf("Дело выполнено! Награда: 4 монеты"), exits)

        f.model.retry()
        f.model.finishComparison(completedQuiz())
        runCurrent()
        assertEquals(writes + 1, f.repo.writes)
        assertEquals(1, exits.size)
    }

    private suspend fun fixture(storyGame: Boolean = false, storyContext: DecisionContext? = null,
        demoPreferences: DemoPreferencesRepository = DisabledDemoPreferencesRepository): Fixture {
        val initial = createInitialGameState().let { it.copy(economy = EconomyState(plan = BudgetPlan(35, 20, 20, 25), unallocated = 0, planning = null)) }
        val repo = DeedRepository(initial)
        val original = bundledGameCatalog()
        val storyChoice = original.content.choices.single { it.eventId == original.introductionId }.id
        val catalog = if (storyGame) original.copy(policies = original.policies +
            (original.introductionId to original.policies.getValue(original.introductionId).copy(
                choiceGameKinds = mapOf(storyChoice to ru.nksk.lctapp.domain.minigame.DeedGameKind.PRECISION)))) else original
        val content = object : StoryContentRepository {
            private var stored = StoryContent()
            override suspend fun read() = stored
            override suspend fun install(content: StoryContent) { stored = content }
        }
        val session = GameSession(repo, content, catalog, initial, demoPreferences)
        assertTrue(session.dispatch(EngineRequest("begin", null, EngineCommand.BeginDay(
            catalog.storyDayId, if (storyGame) listOf(catalog.introductionId) + catalog.deedPool.take(3) else catalog.deedPool.take(4), openFirst = true))) is EngineResult.Applied)
        assertTrue(session.dispatch(EngineRequest("accept", repo.read().engine!!.revision,
            if (storyGame) EngineCommand.StartStoryGame(repo.read().engine!!.currentEvent!!.id, storyChoice)
            else EngineCommand.AcceptDeedProposal(repo.read().engine!!.currentEvent!!.id), context = storyContext)) is EngineResult.Applied)
        val id = repo.read().engine!!.currentEvent!!.id
        val model = DeedGameViewModel(session)
        store.put("game", model)
        model.load(id, storyChoice.takeIf { storyGame })
        repo.writes = 0
        return Fixture(repo, session, model, id)
    }

    private data class Fixture(val repo: DeedRepository, val session: GameSession, val model: DeedGameViewModel, val id: String)

    private class MutableDemoPreferences(enabled: Boolean) : DemoPreferencesRepository {
        private val state = MutableStateFlow(DemoPreferences(enabled))
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun setDemoModeEnabled(enabled: Boolean) { state.value = DemoPreferences(enabled) }
    }
}

private class DeedRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow(initial)
    var failure: Exception? = null
    var afterWrite: suspend () -> Unit = {}
    var writes = 0
    var beforeFacts: suspend () -> Unit = {}
    var beforeFactRead: suspend () -> Unit = {}
    var forbidFullHistoryRead = false
    val commandReceiptReads = mutableListOf<String>()
    var latestCommandReads = 0
    val factReadRequests = mutableListOf<Set<String>>()
    val recordedFacts: List<AnalyticsFact> get() = history.flatMap { it.facts }
    val attempted = mutableListOf<EngineRequest>()
    val committed = mutableListOf<EngineRequest>()
    private data class Receipt(val request: EngineRequest, val context: DecisionContext?, val fingerprint: String?)
    private val receipts = mutableMapOf<String, Receipt>()
    private val history = mutableListOf(AuditEntry("initial", 1, "run", AuditType.INITIALIZED, after = initial))
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun readHistory(): List<AuditEntry> {
        check(!forbidFullHistoryRead) { "Comparison answers must not read all checkpoints" }
        return history.toList()
    }
    override suspend fun readCommandReceipt(requestId: String): AuditEntry? {
        commandReceiptReads += requestId
        return history.lastOrNull { it.type == AuditType.COMMAND && it.request?.id == requestId }
    }
    override suspend fun readLatestCommand(): AuditEntry? {
        latestCommandReads++
        return history.lastOrNull { it.type == AuditType.COMMAND }
    }
    override suspend fun readFacts(eventIds: Set<String>): HistoryFactLookup {
        factReadRequests += eventIds
        beforeFactRead()
        return HistoryFactLookup(history.last().runId, history.last().sequence,
            recordedFacts.filter { it.eventId in eventIds })
    }
    override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
        failure?.let { throw it }
        beforeFacts()
        sourceGuard?.requireMatches(history)
        val existing = history.flatMap { it.facts }.map { it.eventId }.toSet()
        val missing = facts.filter { it.eventId !in existing }
        if (missing.isNotEmpty()) history += AuditEntry("facts-${history.size}", history.last().sequence + 1,
            "run", AuditType.FACTS, facts = missing)
    }
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
