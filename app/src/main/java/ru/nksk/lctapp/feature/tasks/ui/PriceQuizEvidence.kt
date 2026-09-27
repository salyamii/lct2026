package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.analytics.*

/** Values captured before revealing the answer; reward is deliberately absent. */
internal data class PriceQuizAnswerEvidence(
    val index: Int,
    val leftAmount: Int,
    val rightAmount: Int,
    val pickedLeft: Boolean,
    val wasPresented: Boolean,
    val closesSeries: Boolean,
)

internal data class PriceQuizEvidence(val seriesId: String, val answers: List<PriceQuizAnswerEvidence>)

internal object PriceQuizEvidenceMapper {
    fun eventId(occurrenceId: String, seriesId: String, index: Int) = "comparison:$occurrenceId:$seriesId:answer:$index"

    fun facts(evidence: PriceQuizEvidence, occurrenceId: String, runId: String, sequence: Long,
        day: Int?, periodId: String?, contentVersion: String, rulesVersion: String): List<AnalyticsFact> =
        evidence.answers.map { answer ->
            val questionId = "comparison:${evidence.seriesId}:question:${answer.index}"
            val actionId = eventId(occurrenceId, evidence.seriesId, answer.index)
            AnalyticsFact(
                eventId = actionId, gameRunId = runId,
                episodeId = "comparison:$occurrenceId:${evidence.seriesId}", actionId = actionId,
                sequence = sequence,
                detail = FactDetail.QuestionAnswer(questionId, 1,
                    AssessmentTask.CompareAmounts(answer.leftAmount.toLong(), answer.rightAmount.toLong(),
                        if (answer.pickedLeft) ComparisonSide.LEFT else ComparisonSide.RIGHT),
                    answerWasRevealed = false, seriesClosed = answer.closesSeries),
                context = DecisionContext(
                    presentationId = if (answer.wasPresented) "$questionId:presented" else null,
                    informationPresented = answer.wasPresented, complete = true,
                    assistance = setOf(Assistance.INFORMATION_ONLY), financialPeriodId = periodId, day = day,
                ),
                contextFamily = "money_comparison", contentVersion = contentVersion,
                gameRulesVersion = rulesVersion,
            )
        }
}
