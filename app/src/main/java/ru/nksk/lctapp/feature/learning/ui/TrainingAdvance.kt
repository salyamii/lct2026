package ru.nksk.lctapp.feature.learning.ui

/** A restored answer advances only while its question is visible and the route is interactive. */
internal fun TrainingUiState.automaticAdvance(resumed: Boolean): TrainingAction.NextQuestion? {
    if (chapterPractice || !resumed || loading || busy || error != null || needsBudgetPlanning ||
        practiceRetryRequired || !practiceOpen) return null
    val current = question ?: return null
    return if (current.correct && current.series != null) TrainingAction.NextQuestion(current.id) else null
}
