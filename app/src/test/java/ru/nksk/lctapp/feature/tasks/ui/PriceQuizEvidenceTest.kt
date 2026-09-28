package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*

@OptIn(ExperimentalCoroutinesApi::class)
class PriceQuizEvidenceTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun actualQuestionAndChosenSideSurviveRestorationAndDuplicateTap() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("questions" to intArrayOf(80, 20, 15, 60)))
        val model = PriceQuizViewModel(saved)
        model.questionPresented(0)
        model.onAction(PriceQuizAction.Answer(false, 0))
        model.onAction(PriceQuizAction.Answer(true, 0))
        val evidence = model.comparisonEvidence()
        assertEquals(listOf(PriceQuizAnswerEvidence(0, 80, 20, false, true, false)), evidence.answers)
        val restored = PriceQuizViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))
        assertEquals(evidence, restored.comparisonEvidence())
        advanceUntilIdle()
        restored.onAction(PriceQuizAction.Next(0))
        restored.questionPresented(1)
        restored.onAction(PriceQuizAction.Answer(false, 1))
        val facts = facts(restored.comparisonEvidence())
        val result = SkillEvaluator().evaluate(facts).single()
        assertEquals(SkillId.COMPARE_AMOUNTS, result.skill)
        assertEquals(ObservationOutcome.DIFFICULTY, result.outcome)
        assertEquals(2L, result.measures["questions"])
        assertEquals(1L, result.measures["correctFirstAnswers"])
        assertEquals(EpisodeCompletion.COMPLETE, result.completion)
    }

    @Test fun staleCardTapCannotAnswerTheFollowingQuestion() = runTest(dispatcher) {
        val model = PriceQuizViewModel(SavedStateHandle(mapOf("questions" to intArrayOf(80, 20, 15, 60))))
        model.questionPresented(0)
        model.onAction(PriceQuizAction.Answer(true, 0))
        advanceUntilIdle()
        model.onAction(PriceQuizAction.Next(0))
        model.onAction(PriceQuizAction.Answer(true, 0))
        assertEquals(1, model.comparisonEvidence().answers.size)
        assertNull(model.uiState.value.game.lastCorrect)
    }

    @Test fun restoredLegacySeriesDoesNotInventPreviousAnswersOrACompleteSeries() = runTest(dispatcher) {
        val model = PriceQuizViewModel(SavedStateHandle(mapOf("questions" to intArrayOf(80, 20, 15, 60),
            "current" to 1, "correct" to 1)))
        model.questionPresented(1)
        model.onAction(PriceQuizAction.Answer(false, 1))
        val evidence = model.comparisonEvidence()
        assertEquals(1, evidence.answers.size)
        assertFalse(evidence.answers.single().closesSeries)
        assertEquals(EpisodeCompletion.PENDING, SkillEvaluator().evaluate(facts(evidence)).single().completion)
    }

    @Test fun unseenQuestionIsNotReportedAsAnIndependentSuccessAndRestartHasNewIdentity() = runTest(dispatcher) {
        val model = PriceQuizViewModel(SavedStateHandle(mapOf("questions" to intArrayOf(80, 20))))
        model.onAction(PriceQuizAction.Answer(true, 0))
        val old = model.comparisonEvidence()
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, SkillEvaluator().evaluate(facts(old)).single().outcome)
        model.onAction(PriceQuizAction.Restart)
        assertNotEquals(old.seriesId, model.comparisonEvidence().seriesId)
        assertTrue(model.comparisonEvidence().answers.isEmpty())
        advanceUntilIdle()
    }

    @Test fun mapperUsesStableIdsAndActualSumsNotReward() {
        val evidence = PriceQuizEvidence("series", listOf(PriceQuizAnswerEvidence(0, 11, 73, false, true, true)))
        val first = facts(evidence).single()
        val retry = facts(evidence).single()
        assertEquals(first, retry)
        assertEquals("comparison:deed:series:answer:0", first.eventId)
        assertEquals(setOf(first.eventId), PriceQuizEvidenceMapper.eventIds(evidence, "deed"))
        assertEquals(AssessmentTask.CompareAmounts(11, 73, ComparisonSide.RIGHT), (first.detail as FactDetail.QuestionAnswer).task)
        assertFalse((first.detail as FactDetail.QuestionAnswer).answerWasRevealed)
        assertEquals(setOf(Assistance.INFORMATION_ONLY), first.context.assistance)
        assertEquals(AnalyticsMode.REAL, first.mode)
    }

    private fun facts(evidence: PriceQuizEvidence) = PriceQuizEvidenceMapper.facts(evidence,
        "deed", "run", 10, 1, "period", "catalog", "rules")
}
