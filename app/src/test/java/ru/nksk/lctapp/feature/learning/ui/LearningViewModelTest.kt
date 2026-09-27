package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LearningContext
import ru.nksk.lctapp.domain.analytics.SkillEvaluator
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.analytics.ObservationOutcome
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.finance.FinancialPeriods
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun everyTrainingTopicRunsFourQuestionsAndCanStartAnotherSeriesWithoutSpending() = runTest(dispatcher) {
        val topics = listOf(
            LearningAction.Review to FinancialQuestionKind.PLAN_REVIEW,
            LearningAction.ReviewConsequences to FinancialQuestionKind.CONSEQUENCE,
            LearningAction.ReviewTransactions to FinancialQuestionKind.TRANSACTION_ACCOUNTING,
            LearningAction.PracticeSaving to FinancialQuestionKind.SAVING_PRACTICE,
        )
        for ((action, kind) in topics) {
            val (repository, model) = fixture()
            val original = repository.state.value
            model.onAction(action)
            val first = checkNotNull(model.uiState.first { !it.busy }.question)
            val request = repository.committed.values.single().command as EngineCommand.RequestFinancialPractice
            assertEquals(kind, request.kind)
            assertTrue(request.series)
            val ids = mutableSetOf<String>()
            repeat(4) { index ->
                val question = checkNotNull(model.uiState.value.question)
                ids += question.id
                assertEquals(kind, question.kind)
                assertEquals(index + 1, question.series!!.questionNumber)
                assertTrue(model.uiState.value.practiceOpen)
                model.onAction(LearningAction.QuestionPresented(question.id))
                model.onAction(LearningAction.Answer(question.correctAnswerId))
                val correct = checkNotNull(model.uiState.first { !it.busy }.question)
                assertTrue(correct.correct)
                assertEquals(1, correct.attempts)
                if (index < 3) {
                    // The screen emits this only after a correct answer; the VM passes its stable identity.
                    model.onAction(LearningAction.NextQuestion(question.id))
                    val next = checkNotNull(model.uiState.first { !it.busy }.question)
                    assertNotEquals(question.id, next.id)
                    assertEquals(0, next.attempts)
                }
            }
            assertEquals(4, ids.size)
            assertTrue(model.uiState.value.question!!.series!!.isLast)
            assertEquals(original.economy, repository.state.value.economy)
            assertEquals(original.pet, repository.state.value.pet)
            assertEquals(original.engine!!.steps, repository.state.value.engine!!.steps)
            val answers = repository.facts.filter { it.detail is FactDetail.QuestionAnswer || it.detail is FactDetail.PracticeAnswer }
            assertEquals(4, answers.size)
            assertTrue(answers.all { it.learningContext == LearningContext.TRAINING && it.context.complete })
            val observations = SkillEvaluator().evaluate(answers)
            assertTrue(observations.all { it.learningContexts == setOf(LearningContext.TRAINING) })
            assertTrue(observations.none { it.outcome == ObservationOutcome.SUPPORTED &&
                it.skill != SkillId.UNDERSTAND_INCOME_AND_EXPENSES })
            if (kind == FinancialQuestionKind.TRANSACTION_ACCOUNTING) {
                assertEquals(4, observations.size)
                assertTrue(observations.all { it.skill == SkillId.UNDERSTAND_INCOME_AND_EXPENSES &&
                    it.outcome == ObservationOutcome.SUPPORTED })
            }

            model.onAction(action)
            val restarted = checkNotNull(model.uiState.first { !it.busy }.question)
            assertEquals(1, restarted.series!!.questionNumber)
            assertNotEquals(first.series!!.id, restarted.series!!.id)
            assertFalse(restarted.correct)
        }
    }

    @Test fun wrongAnswerCanBeClosedRestoredAndRetriedWithoutResettingItsIdentityOrProgress() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(LearningAction.ReviewTransactions)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(LearningAction.QuestionPresented(question.id))
        val wrong = question.options.first { it.id != question.correctAnswerId }
        model.onAction(LearningAction.Answer(wrong.id))
        val failed = checkNotNull(model.uiState.first { !it.busy }.question)
        assertFalse(failed.correct)
        assertEquals(1, failed.attempts)
        assertTrue(failed.explanation.isNotBlank())
        model.onAction(LearningAction.CloseQuestion)
        val closed = model.uiState.first { !it.busy }
        assertFalse(closed.practiceOpen)
        assertEquals(failed, closed.question)
        model.onAction(LearningAction.ReviewTransactions)
        val resumed = model.uiState.first { !it.busy }
        assertTrue(resumed.practiceOpen)
        assertEquals(failed, resumed.question)

        val (restoredRepository, restoredModel) = fixture(savedGame = repository.state.value)
        assertEquals(failed, restoredModel.uiState.value.question)
        restoredModel.onAction(LearningAction.QuestionPresented(question.id))
        restoredModel.onAction(LearningAction.Answer(question.correctAnswerId))
        val answered = checkNotNull(restoredModel.uiState.first { !it.busy }.question)
        assertEquals(question.id, answered.id)
        assertTrue(answered.correct)
        assertEquals(2, answered.attempts)
        val fact = restoredRepository.facts.single { it.detail is FactDetail.QuestionAnswer }
        assertTrue((fact.detail as FactDetail.QuestionAnswer).answerWasRevealed)
        assertEquals(LearningContext.TRAINING, fact.learningContext)
        restoredModel.onAction(LearningAction.NextQuestion(question.id))
        assertEquals(2, restoredModel.uiState.first { !it.busy }.question!!.series!!.questionNumber)
    }

    @Test fun nextQuestionCommittedBeforeFailureRetriesTheSameCommandAndDoesNotSkipAQuestion() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(LearningAction.Review)
        val first = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(LearningAction.Answer(first.correctAnswerId))
        model.uiState.first { !it.busy }
        repository.failAfterCommit = true
        model.onAction(LearningAction.NextQuestion(first.id))
        model.uiState.first { !it.busy }
        val failedRequest = repository.attempted.last()
        val second = repository.state.value.financial.practice!!
        assertEquals(2, second.series!!.questionNumber)
        assertTrue(model.uiState.value.practiceRetryRequired)
        model.onAction(LearningAction.Retry)
        val recovered = model.uiState.first { !it.busy }
        assertEquals(failedRequest, repository.attempted.last())
        assertEquals(second, recovered.question)
        assertEquals(1, repository.committed.values.count { it.command is EngineCommand.AdvanceFinancialPractice })
        assertEquals(0, recovered.question!!.attempts)
        assertFalse(recovered.practiceRetryRequired)
    }

    @Test fun trainingRemainsAvailableWhenNoGoalPeriodIsActive() = runTest(dispatcher) {
        val (repository, model) = fixture(activeGoal = false)
        val original = repository.state.value
        assertNull(original.financial.currentPeriod)
        assertTrue(model.uiState.value.canReview)
        model.onAction(LearningAction.ReviewTransactions)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        assertEquals(4, question.series!!.totalQuestions)
        assertTrue(question.sourceActionIds.single().startsWith("training:"))
        assertNull(repository.state.value.financial.currentPeriod)
        assertEquals(original.economy, repository.state.value.economy)
        assertNull(model.uiState.value.error)
    }

    @Test fun failedQuestionRequestRetriesTheOriginalRequestAndDoesNotAcceptAnotherKind() = runTest(dispatcher) {
        val (repository, model) = fixture()
        repository.failBeforeCommit = true
        model.onAction(LearningAction.Review)
        model.uiState.first { !it.busy }
        val failed = repository.attempted.single()
        assertNull(model.uiState.value.question)
        assertTrue(model.uiState.value.practiceRetryRequired)

        model.onAction(LearningAction.ReviewTransactions)
        assertEquals(listOf(failed), repository.attempted)
        model.onAction(LearningAction.Retry)
        model.uiState.first { !it.busy }
        assertEquals(listOf(failed, failed), repository.attempted)
        assertEquals(1, repository.committed.size)
        assertNotNull(model.uiState.value.question)
        assertFalse(model.uiState.value.practiceRetryRequired)
    }

    @Test fun failedAnswerKeepsItsOriginalContextAndBlocksAnotherAnswerUntilRetry() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(LearningAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        val wrong = question.options.first { it.id != question.correctAnswerId }
        repository.failBeforeCommit = true
        model.onAction(LearningAction.Answer(wrong.id))
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        assertFalse(checkNotNull(failed.context).informationPresented)

        // A later visible presentation must not upgrade the evidence on the failed answer.
        model.onAction(LearningAction.QuestionPresented(question.id))
        model.onAction(LearningAction.Answer(question.correctAnswerId))
        assertEquals(2, repository.attempted.size)
        model.onAction(LearningAction.Retry)
        val answered = model.uiState.first { !it.busy }
        assertEquals(failed, repository.attempted.last())
        assertEquals(wrong.id, answered.question!!.answeredOptionId)
        assertEquals(1, answered.question.attempts)
        assertFalse(checkNotNull(repository.attempted.last().context).complete)
        assertFalse(answered.practiceRetryRequired)
        assertEquals(1, repository.facts.count { it.detail is FactDetail.PracticeAnswer })
    }

    @Test fun answerCommittedBeforeFailureIsNotAppliedOrRecordedTwiceOnRetry() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(LearningAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(LearningAction.QuestionPresented(question.id))
        repository.failAfterCommit = true
        model.onAction(LearningAction.Answer(question.correctAnswerId))
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        assertEquals(1, repository.state.value.financial.practice!!.attempts)
        assertTrue(model.uiState.value.practiceRetryRequired)

        model.onAction(LearningAction.Retry)
        val answered = model.uiState.first { !it.busy }
        assertEquals(failed, repository.attempted.last())
        assertTrue(answered.question!!.correct)
        assertEquals(1, answered.question.attempts)
        assertEquals(1, repository.committed.values.count { it.command is EngineCommand.AnswerFinancialQuestion })
        assertEquals(1, repository.facts.count { it.detail is FactDetail.PracticeAnswer })
        assertFalse(answered.practiceRetryRequired)
        assertNull(answered.error)
    }

    @Test fun failedCloseRetriesItsIdentityAndHidesTheSeriesOnlyAfterCommit() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(LearningAction.Review)
        val question = model.uiState.first { !it.busy }.question
        repository.failBeforeCommit = true
        model.onAction(LearningAction.CloseQuestion)
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        assertNotNull(model.uiState.value.question)
        assertTrue(model.uiState.value.practiceOpen)
        model.onAction(LearningAction.Retry)
        val closed = model.uiState.first { !it.busy }
        assertEquals(failed, repository.attempted.last())
        assertEquals(question, closed.question)
        assertEquals(question, repository.state.value.financial.practice)
        assertFalse(closed.practiceOpen)
        assertFalse(closed.practiceRetryRequired)
    }

    @Test fun pendingAnswerWithStaleRevisionIsRejectedWithoutBeingRebased() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(LearningAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(LearningAction.QuestionPresented(question.id))
        repository.failBeforeCommit = true
        model.onAction(LearningAction.Answer(question.correctAnswerId))
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        repository.state.value = repository.state.value.let { game ->
            val engine = checkNotNull(game.engine)
            game.copy(engine = engine.copy(revision = engine.revision + 1))
        }
        model.onAction(LearningAction.Retry)
        val blocked = model.uiState.first { !it.busy }
        assertEquals(failed, repository.attempted.last())
        assertNotNull(blocked.error)
        assertFalse(blocked.practiceRetryRequired)
        assertEquals(0, blocked.question!!.attempts)
        assertEquals(repository.state.value, blocked.realGame)
        assertEquals(0, repository.facts.count { it.detail is FactDetail.PracticeAnswer })

        // A new explicit answer is allowed only after the rejection and fresh read.
        model.onAction(LearningAction.Answer(question.correctAnswerId))
        model.uiState.first { !it.busy }
        val fresh = repository.attempted.last()
        assertNotEquals(failed.id, fresh.id)
        assertEquals(failed.expectedRevision!! + 1, fresh.expectedRevision)
        assertFalse(checkNotNull(fresh.context).informationPresented)
    }

    @Test fun unfinishedBudgetOffersPlanningInsteadOfCommandsThatTheEngineWouldReject() = runTest(dispatcher) {
        val (repository, model) = fixture(planning = true)
        assertTrue(model.uiState.value.needsBudgetPlanning)
        assertFalse(model.uiState.value.canReview)
        model.onAction(LearningAction.Review)
        assertTrue(repository.attempted.isEmpty())
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.practiceRetryRequired)
    }

    private suspend fun fixture(planning: Boolean = false, activeGoal: Boolean = true,
        savedGame: GameState? = null): Pair<LearningRepository, LearningViewModel> {
        val catalog = bundledGameCatalog()
        val initial = savedGame ?: FinancialPeriods.adopt(createInitialGameState().copy(
            economy = EconomyState(BudgetPlan(35, 20, 20, 25), availableBalance = 100, savingsBalance = 0),
            selectedGoalId = catalog.goals.first().goalId.takeIf { activeGoal },
            engine = EngineState(catalog.rules.id, 7, 1, DayPhase.RUNNING, 0, catalog.rules.fullEnergy,
                false, null, 100, emptyList(), emptyList()),
        ), imported = false).let { game ->
            if (planning) game.copy(economy = EconomyOperations.beginManual(game.economy, "unfinished")) else game
        }
        val repository = LearningRepository(initial)
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read(): StoryContent = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        val model = LearningViewModel(session)
        store.put("learning", model)
        model.uiState.first { !it.loading }
        return repository to model
    }
}

/** Mirrors repository command identity semantics; failures can happen before or after commit. */
private class LearningRepository(initial: GameState) : GameRepository {
    val state = MutableStateFlow(initial)
    val attempted = mutableListOf<EngineRequest>()
    val committed = linkedMapOf<String, EngineRequest>()
    val facts = mutableListOf<AnalyticsFact>()
    var failBeforeCommit = false
    var failAfterCommit = false

    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun update(transform: (GameState) -> GameState): GameState = transform(state.value).also { state.value = it }
    override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
        facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
        attempted += request
        committed[request.id]?.let {
            check(it == request && it.context == context)
            return state.value
        }
        if (failBeforeCommit) { failBeforeCommit = false; throw IOException("Write failed") }
        val before = state.value
        val after = transform(before)
        val committedFacts = facts(before, after, "learning-run", committed.size.toLong() + 1)
        committed[request.id] = request
        this.facts += committedFacts
        state.value = after
        if (failAfterCommit) { failAfterCommit = false; throw IOException("Reply lost after commit") }
        return after
    }
}
