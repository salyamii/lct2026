package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.domain.finance.FinancialMilestone
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.game.GameState

/** Presents the remaining domain requirements; answering examples cannot replace food or a real plan. */
internal enum class ChapterPracticeStep(val questionKind: FinancialQuestionKind? = null) {
    SAVING(FinancialQuestionKind.SAVING_PRACTICE),
    REVIEW(FinancialQuestionKind.PLAN_REVIEW),
    BUDGET,
    FOOD,
    COMPLETE,
}

internal fun GameState.chapterPracticeStep(): ChapterPracticeStep {
    val period = financial.currentPeriod ?: return ChapterPracticeStep.COMPLETE
    if (period.imported) return ChapterPracticeStep.COMPLETE
    val missing = period.missingMilestones
    return when {
        FinancialMilestone.SAVE_FOR_GOAL in missing -> ChapterPracticeStep.SAVING
        FinancialMilestone.REVIEW_PLAN in missing -> if (period.reviewEvidence?.answerCorrect == true)
            ChapterPracticeStep.BUDGET else ChapterPracticeStep.REVIEW
        FinancialMilestone.PROVIDE_NEEDS in missing -> ChapterPracticeStep.FOOD
        else -> ChapterPracticeStep.COMPLETE
    }
}
