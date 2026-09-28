package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
class TrainingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun chapterPracticeAnswersOnlyMissingTopicsAndNeverStartsTheEndlessTrainingLoop() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.setChapterPractice()
        assertEquals(ChapterPracticeStep.SAVING, model.uiState.value.chapterStep)
        assertFalse(model.uiState.value.practiceOpen)

        model.onAction(TrainingAction.StartChapterPractice)
        val saving = checkNotNull(model.uiState.first { !it.busy }.question)
        assertEquals(FinancialQuestionKind.SAVING_PRACTICE, saving.kind)
        model.onAction(TrainingAction.QuestionPresented(saving.id))
        model.onAction(TrainingAction.Answer(saving.correctAnswerId))
        val saved = model.uiState.first { !it.busy }
        assertEquals(ChapterPracticeStep.REVIEW, saved.chapterStep)
        assertNull(saved.automaticAdvance(resumed = true))
        val attempts = repository.attempted.size
        model.onAction(TrainingAction.NextQuestion(saving.id))
        runCurrent()
        assertEquals(attempts, repository.attempted.size)

        model.onAction(TrainingAction.StartChapterPractice)
        val review = checkNotNull(model.uiState.first { !it.busy }.question)
        assertEquals(FinancialQuestionKind.PLAN_REVIEW, review.kind)
        model.onAction(TrainingAction.QuestionPresented(review.id))
        model.onAction(TrainingAction.Answer(review.correctAnswerId))
        val reviewed = model.uiState.first { !it.busy }
        assertEquals(ChapterPracticeStep.BUDGET, reviewed.chapterStep)
        assertNull(reviewed.automaticAdvance(resumed = true))
        assertEquals(2, repository.committed.values.count { it.command is EngineCommand.RequestFinancialPractice })
        assertFalse(repository.committed.values.any { it.command is EngineCommand.AdvanceFinancialPractice })

        val (restoredRepository, restoredModel) = fixture(savedGame = repository.state.value)
        restoredModel.setChapterPractice()
        assertEquals(ChapterPracticeStep.BUDGET, restoredModel.uiState.value.chapterStep)
        restoredModel.onAction(TrainingAction.StartChapterPractice)
        runCurrent()
        assertTrue(restoredRepository.attempted.isEmpty())
    }

    @Test fun chapterPracticeAlreadySatisfiedStaysCompleteWhenReopened() = runTest(dispatcher) {
        val (repository, _) = fixture()
        val game = repository.state.value
        val period = checkNotNull(game.financial.currentPeriod)
        val completed = game.copy(financial = game.financial.copy(periods = listOf(period.copy(
            needsProvided = true,
            savingPractice = checkNotNull(period.savingPractice).copy(recoveryQuestionId = "saving-answer"),
            reviewEvidence = ru.nksk.lctapp.domain.finance.PeriodReviewEvidence("review-answer", "plan", true,
                true, managedPlan = true),
        ))))
        val (restored, model) = fixture(savedGame = completed)
        model.setChapterPractice()
        assertEquals(ChapterPracticeStep.COMPLETE, model.uiState.value.chapterStep)
        model.onAction(TrainingAction.StartChapterPractice)
        runCurrent()
        assertTrue(restored.attempted.isEmpty())
        assertNull(model.uiState.value.automaticAdvance(resumed = true))
    }

    @Test fun everyTrainingTopicRunsFourQuestionsAndCanStartAnotherSeriesWithoutSpending() = runTest(dispatcher) {
        val topics = listOf(
            TrainingAction.Review to FinancialQuestionKind.PLAN_REVIEW,
            TrainingAction.ReviewConsequences to FinancialQuestionKind.CONSEQUENCE,
            TrainingAction.ReviewTransactions to FinancialQuestionKind.TRANSACTION_ACCOUNTING,
            TrainingAction.PracticeSaving to FinancialQuestionKind.SAVING_PRACTICE,
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
                model.onAction(TrainingAction.QuestionPresented(question.id))
                model.onAction(TrainingAction.Answer(question.correctAnswerId))
                val correct = checkNotNull(model.uiState.first { !it.busy }.question)
                assertTrue(correct.correct)
                assertEquals(1, correct.attempts)
                if (index < 3) {
                    // The screen emits this only after a correct answer; the VM passes its stable identity.
                    model.onAction(TrainingAction.NextQuestion(question.id))
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

            model.onAction(TrainingAction.NextQuestion(checkNotNull(model.uiState.value.question).id))
            val restarted = checkNotNull(model.uiState.first { !it.busy }.question)
            assertEquals(1, restarted.series!!.questionNumber)
            assertNotEquals(first.series!!.id, restarted.series!!.id)
            assertFalse(restarted.correct)
        }
    }

    @Test fun correctAnswerWaitsForResumeAndRecoveryBeforeAdvancingItsOwnQuestion() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
        val correct = model.uiState.first { !it.busy }
        val advance = TrainingAction.NextQuestion(question.id)
        assertNull(correct.automaticAdvance(resumed = false))
        assertEquals(advance, correct.automaticAdvance(resumed = true))
        assertNull(correct.copy(error = "Временная ошибка").automaticAdvance(resumed = true))
        assertNull(correct.copy(practiceRetryRequired = true).automaticAdvance(resumed = true))
        assertNull(correct.copy(busy = true).automaticAdvance(resumed = true))
        assertEquals(advance, correct.copy(error = null).automaticAdvance(resumed = true))

        model.onAction(advance)
        val next = model.uiState.first { !it.busy }
        assertNotEquals(question.id, next.question?.id)
        assertNull(next.automaticAdvance(resumed = true))
        val attempts = repository.attempted.size
        model.onAction(advance)
        runCurrent()
        assertEquals(attempts, repository.attempted.size)
        assertEquals(next.question, model.uiState.value.question)
    }

    @Test fun retryingCloseOfCorrectAnswerDoesNotAutomaticallyReopenOrAdvanceIt() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
        model.uiState.first { !it.busy }
        repository.failBeforeCommit = true
        model.onAction(TrainingAction.CloseQuestion)
        val failed = model.uiState.first { !it.busy }
        assertNull(failed.automaticAdvance(resumed = true))
        model.onAction(TrainingAction.Retry)
        val closed = model.uiState.first { !it.busy }
        assertFalse(closed.practiceOpen)
        assertNull(closed.error)
        assertNull(closed.automaticAdvance(resumed = true))
        val attempts = repository.attempted.size
        model.onAction(TrainingAction.NextQuestion(question.id))
        runCurrent()
        assertEquals(attempts, repository.attempted.size)
        assertFalse(model.uiState.value.practiceOpen)
    }

    @Test fun wrongAnswerCanBeClosedRestoredAndRetriedWithoutResettingItsIdentityOrProgress() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.ReviewTransactions)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(TrainingAction.QuestionPresented(question.id))
        val wrong = question.options.first { it.id != question.correctAnswerId }
        model.onAction(TrainingAction.Answer(wrong.id))
        val failed = checkNotNull(model.uiState.first { !it.busy }.question)
        assertFalse(failed.correct)
        assertEquals(1, failed.attempts)
        assertTrue(failed.explanation.isNotBlank())
        model.onAction(TrainingAction.CloseQuestion)
        val closed = model.uiState.first { !it.busy }
        assertFalse(closed.practiceOpen)
        assertEquals(failed, closed.question)
        model.onAction(TrainingAction.ReviewTransactions)
        val resumed = model.uiState.first { !it.busy }
        assertTrue(resumed.practiceOpen)
        assertEquals(failed, resumed.question)

        val (restoredRepository, restoredModel) = fixture(savedGame = repository.state.value)
        assertEquals(failed, restoredModel.uiState.value.question)
        restoredModel.onAction(TrainingAction.QuestionPresented(question.id))
        restoredModel.onAction(TrainingAction.Answer(question.correctAnswerId))
        val answered = checkNotNull(restoredModel.uiState.first { !it.busy }.question)
        assertEquals(question.id, answered.id)
        assertTrue(answered.correct)
        assertEquals(2, answered.attempts)
        val fact = restoredRepository.facts.single { it.detail is FactDetail.QuestionAnswer }
        assertTrue((fact.detail as FactDetail.QuestionAnswer).answerWasRevealed)
        assertEquals(LearningContext.TRAINING, fact.learningContext)
        restoredModel.onAction(TrainingAction.NextQuestion(question.id))
        assertEquals(2, restoredModel.uiState.first { !it.busy }.question!!.series!!.questionNumber)
    }

    @Test fun nextQuestionCommittedBeforeFailureRetriesTheSameCommandAndDoesNotSkipAQuestion() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.Review)
        val first = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(TrainingAction.Answer(first.correctAnswerId))
        model.uiState.first { !it.busy }
        repository.failAfterCommit = true
        model.onAction(TrainingAction.NextQuestion(first.id))
        model.uiState.first { !it.busy }
        val failedRequest = repository.attempted.last()
        val second = repository.state.value.financial.practice!!
        assertEquals(2, second.series!!.questionNumber)
        assertTrue(model.uiState.value.practiceRetryRequired)
        model.onAction(TrainingAction.Retry)
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
        model.onAction(TrainingAction.ReviewTransactions)
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
        model.onAction(TrainingAction.Review)
        model.uiState.first { !it.busy }
        val failed = repository.attempted.single()
        assertNull(model.uiState.value.question)
        assertTrue(model.uiState.value.practiceRetryRequired)

        model.onAction(TrainingAction.ReviewTransactions)
        assertEquals(listOf(failed), repository.attempted)
        model.onAction(TrainingAction.Retry)
        model.uiState.first { !it.busy }
        assertEquals(listOf(failed, failed), repository.attempted)
        assertEquals(1, repository.committed.size)
        assertNotNull(model.uiState.value.question)
        assertFalse(model.uiState.value.practiceRetryRequired)
    }

    @Test fun failedAnswerKeepsItsOriginalContextAndBlocksAnotherAnswerUntilRetry() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        val wrong = question.options.first { it.id != question.correctAnswerId }
        repository.failBeforeCommit = true
        model.onAction(TrainingAction.Answer(wrong.id))
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        assertFalse(checkNotNull(failed.context).informationPresented)

        // A later visible presentation must not upgrade the evidence on the failed answer.
        model.onAction(TrainingAction.QuestionPresented(question.id))
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
        assertEquals(2, repository.attempted.size)
        model.onAction(TrainingAction.Retry)
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
        model.onAction(TrainingAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(TrainingAction.QuestionPresented(question.id))
        repository.failAfterCommit = true
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        assertEquals(1, repository.state.value.financial.practice!!.attempts)
        assertTrue(model.uiState.value.practiceRetryRequired)

        model.onAction(TrainingAction.Retry)
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
        model.onAction(TrainingAction.Review)
        val question = model.uiState.first { !it.busy }.question
        repository.failBeforeCommit = true
        model.onAction(TrainingAction.CloseQuestion)
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        assertNotNull(model.uiState.value.question)
        assertTrue(model.uiState.value.practiceOpen)
        model.onAction(TrainingAction.Retry)
        val closed = model.uiState.first { !it.busy }
        assertEquals(failed, repository.attempted.last())
        assertEquals(question, closed.question)
        assertEquals(question, repository.state.value.financial.practice)
        assertFalse(closed.practiceOpen)
        assertFalse(closed.practiceRetryRequired)
    }

    @Test fun pendingAnswerWithStaleRevisionIsRejectedWithoutBeingRebased() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        model.onAction(TrainingAction.QuestionPresented(question.id))
        repository.failBeforeCommit = true
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
        model.uiState.first { !it.busy }
        val failed = repository.attempted.last()
        repository.state.value = repository.state.value.let { game ->
            val engine = checkNotNull(game.engine)
            game.copy(engine = engine.copy(revision = engine.revision + 1))
        }
        model.onAction(TrainingAction.Retry)
        val blocked = model.uiState.first { !it.busy }
        assertEquals(failed, repository.attempted.last())
        assertNotNull(blocked.error)
        assertFalse(blocked.practiceRetryRequired)
        assertEquals(0, blocked.question!!.attempts)
        assertTrue(blocked.hasGame)
        assertEquals(0, repository.facts.count { it.detail is FactDetail.PracticeAnswer })

        // A new explicit answer is allowed only after the rejection and fresh read.
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
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
        model.onAction(TrainingAction.Review)
        assertTrue(repository.attempted.isEmpty())
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.practiceRetryRequired)
    }

    @Test fun answeringAdvancingAndClosingDoNotReadHistoryOrFailWhenHistoryIsUnavailable() = runTest(dispatcher) {
        val (repository, model) = fixture()
        assertEquals(0, repository.historyReads)
        model.onAction(TrainingAction.ReviewTransactions)
        val first = checkNotNull(model.uiState.first { !it.busy }.question)
        // Starting a series legitimately reads history inside the engine; rendering and answering do not.
        val readsAfterStart = repository.historyReads
        repository.failHistoryRead = true
        model.onAction(TrainingAction.Answer(first.correctAnswerId))
        assertTrue(checkNotNull(model.uiState.first { !it.busy }.question).correct)
        model.onAction(TrainingAction.NextQuestion(first.id))
        val second = checkNotNull(model.uiState.first { !it.busy }.question)
        assertEquals(2, second.series!!.questionNumber)
        model.onAction(TrainingAction.CloseQuestion)
        val closed = model.uiState.first { !it.busy }
        assertFalse(closed.practiceOpen)
        assertNull(closed.error)
        assertEquals(readsAfterStart, repository.historyReads)
    }

    @Test fun planReviewAnswerKeepsItsCachedWordingWhenHistoryBecomesUnavailable() = runTest(dispatcher) {
        val (repository, model) = fixture()
        model.onAction(TrainingAction.Review)
        val question = checkNotNull(model.uiState.first { !it.busy }.question)
        assertNotNull(question.reviewEvidence)
        val readsAfterQuestion = repository.historyReads
        repository.failHistoryRead = true
        model.onAction(TrainingAction.Answer(question.correctAnswerId))
        val answered = model.uiState.first { !it.busy }
        assertTrue(checkNotNull(answered.question).correct)
        assertEquals(question.prompt, answered.question.prompt)
        assertEquals(readsAfterQuestion, repository.historyReads)
        assertNull(answered.error)
    }

    private suspend fun fixture(planning: Boolean = false, activeGoal: Boolean = true,
        savedGame: GameState? = null): Pair<LearningRepository, TrainingViewModel> {
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
        val model = TrainingViewModel(session)
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
    var historyReads = 0
    var failHistoryRead = false

    override suspend fun readHistory(): List<ru.nksk.lctapp.domain.history.AuditEntry> {
        historyReads += 1
        if (failHistoryRead) throw IOException("History unavailable")
        return emptyList()
    }
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
