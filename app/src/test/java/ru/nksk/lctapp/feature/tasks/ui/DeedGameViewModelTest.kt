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
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.HistorySourceGuard

@OptIn(ExperimentalCoroutinesApi::class)
class DeedGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

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
        assertNotNull(f.model.uiState.value.message)
        assertFalse(f.model.uiState.value.presentation!!.canPlay)
        f.repo.failure = null
        f.model.retry()
        runCurrent()
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

    @Test fun zeroRewardStillConfirmsCompletedWork() = runTest(dispatcher) {
        val f = fixture()
        runCurrent()
        val exits = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.model.exit.collect { exits += it } }
        f.model.finishComparison(completedQuiz().copy(correctAnswers = 0))
        runCurrent()
        assertEquals(listOf("Дело выполнено! Награда: 0 монет"), exits)
        assertTrue(f.repo.read().engine!!.deeds.single().completed)
        assertEquals(100L, f.repo.read().economy.balance)
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

    private suspend fun fixture(storyGame: Boolean = false): Fixture {
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
        val session = GameSession(repo, content, catalog, initial)
        assertTrue(session.dispatch(EngineRequest("begin", null, EngineCommand.BeginDay(
            catalog.storyDayId, if (storyGame) listOf(catalog.introductionId) + catalog.deedPool.take(3) else catalog.deedPool.take(4), openFirst = true))) is EngineResult.Applied)
        assertTrue(session.dispatch(EngineRequest("accept", repo.read().engine!!.revision,
            if (storyGame) EngineCommand.StartStoryGame(repo.read().engine!!.currentEvent!!.id, storyChoice)
            else EngineCommand.AcceptDeedProposal(repo.read().engine!!.currentEvent!!.id))) is EngineResult.Applied)
        val id = repo.read().engine!!.currentEvent!!.id
        val model = DeedGameViewModel(session)
        store.put("game", model)
        model.load(id, storyChoice.takeIf { storyGame })
        repo.writes = 0
        return Fixture(repo, session, model, id)
    }

    private data class Fixture(val repo: DeedRepository, val session: GameSession, val model: DeedGameViewModel, val id: String)
}

private class DeedRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow(initial)
    var failure: Exception? = null
    var afterWrite: suspend () -> Unit = {}
    var writes = 0
    var beforeFacts: suspend () -> Unit = {}
    private val history = mutableListOf(AuditEntry("initial", 1, "run", AuditType.INITIALIZED, after = initial))
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun readHistory() = history.toList()
    override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
        failure?.let { throw it }
        beforeFacts()
        sourceGuard?.requireMatches(history)
        val existing = history.flatMap { it.facts }.map { it.eventId }.toSet()
        val missing = facts.filter { it.eventId !in existing }
        if (missing.isNotEmpty()) history += AuditEntry("facts-${history.size}", history.last().sequence + 1,
            "run", AuditType.FACTS, facts = missing)
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
