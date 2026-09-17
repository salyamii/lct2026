package ru.nksk.lctapp.domain.story

/** Progress references; story content and progression rules are defined by future features. */
data class StoryState(
    val currentChapterId: String?,
    val currentEventId: String?,
    val decisions: List<StoryDecision>,
)

/** Identifies a recorded choice without interpreting its financial or pet-state outcome. */
data class StoryDecision(
    val eventId: String,
    val choiceId: String,
)
