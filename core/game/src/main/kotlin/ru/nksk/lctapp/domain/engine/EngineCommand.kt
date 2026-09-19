package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameScore

/** Revision is captured with the UI state. A stale command never changes a newer save. */
data class EngineRequest(val id: String, val expectedRevision: Long?, val command: EngineCommand) {
    init { require(id.isNotBlank()) }
}

sealed interface EngineCommand {
    /** Explicit authored day plan, including the event IDs of any carried lore. */
    data class BeginDay(val storyDayId: String, val eventIds: List<String>, val openFirst: Boolean = false) : EngineCommand
    data object OpenNextEvent : EngineCommand
    data class Choose(val occurrenceId: String, val choiceId: String) : EngineCommand
    /** Commit a choice and close its result atomically when no authored result screen is needed. */
    data class CompleteEvent(val occurrenceId: String, val choiceId: String) : EngineCommand
    data class AcknowledgeResult(val occurrenceId: String) : EngineCommand
    data class StartDeed(val offerId: String) : EngineCommand
    data class AcceptDeedProposal(val occurrenceId: String) : EngineCommand
    data class CompleteDeed(val occurrenceId: String, val score: DeedGameScore) : EngineCommand
    data class DismissDeedProposal(val occurrenceId: String) : EngineCommand
    /** Leave without recording a choice, spending effort or completing the story. */
    data class PauseEvent(val occurrenceId: String) : EngineCommand
    data class Feed(val mealId: String) : EngineCommand
    data object FinishDay : EngineCommand
}

sealed interface EngineResult {
    data class Applied(val state: GameState) : EngineResult
    data class Blocked(val reason: BlockReason) : EngineResult
}

sealed interface BlockReason {
    data object StaleRevision : BlockReason
    data object DayNotStarted : BlockReason
    data object DayFinished : BlockReason
    data object EventInProgress : BlockReason
    data object NoNextEvent : BlockReason
    data object InvalidEventAction : BlockReason
    data object MustEat : BlockReason
    data object MustSleep : BlockReason
    data object DeedUnavailable : BlockReason
    data object OnlyShortDeedsAfterSchedule : BlockReason
    data object UnfinishedEvents : BlockReason
    data object MissingCarriedLore : BlockReason
    data object PreviousLoreIncomplete : BlockReason
    data object ChapterGoalIncomplete : BlockReason
    data class MissingItems(val itemIds: Set<String>) : BlockReason
    data class InsufficientMoney(val missing: Long) : BlockReason
    data class InvalidContent(val detail: String) : BlockReason
}
