package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.domain.finance.FinancialMilestone
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.game.GameState

/** Presents remaining practice and food; a finished quiz never demands another budget revision. */
internal enum class ChapterPracticeStep(val questionKind: FinancialQuestionKind? = null) {
    SAVING(FinancialQuestionKind.SAVING_PRACTICE),
    REVIEW(FinancialQuestionKind.PLAN_REVIEW),
    FOOD,
    COMPLETE,
}

internal fun GameState.chapterPracticeStep(): ChapterPracticeStep {
    val period = financial.currentPeriod ?: return ChapterPracticeStep.COMPLETE
    if (period.imported) return ChapterPracticeStep.COMPLETE
    val missing = period.missingMilestones
    return when {
        FinancialMilestone.SAVE_FOR_GOAL in missing -> ChapterPracticeStep.SAVING
        FinancialMilestone.REVIEW_PLAN in missing -> ChapterPracticeStep.REVIEW
        FinancialMilestone.PROVIDE_NEEDS in missing -> ChapterPracticeStep.FOOD
        else -> ChapterPracticeStep.COMPLETE
    }
}
