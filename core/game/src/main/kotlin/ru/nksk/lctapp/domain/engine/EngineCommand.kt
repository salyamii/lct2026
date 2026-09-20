package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.pet.PetColor

/** Revision is captured with the UI state. A stale command never changes a newer save. */
data class EngineRequest(val id: String, val expectedRevision: Long?, val command: EngineCommand) {
    init { require(id.isNotBlank()) }
}

sealed interface EngineCommand {
    data class RenamePet(val name: String, val expectedName: String) : EngineCommand
    data class SetPetColor(val color: PetColor, val expectedColor: PetColor) : EngineCommand
    /** Explicit authored day plan, including the event IDs of any carried lore. */
    data class BeginDay(val storyDayId: String, val eventIds: List<String>, val openFirst: Boolean = false) : EngineCommand
    data object OpenNextEvent : EngineCommand
    data class SelectGoal(val goalId: String, val firstDay: BeginDay? = null) : EngineCommand
    data class BuyGoalItem(val goalId: String, val itemId: String, val acceptFoodRisk: Boolean = false) : EngineCommand
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
    /** Defer the blocked card and finish the day in a single transaction. */
    data class FinishDayFromEvent(val occurrenceId: String) : EngineCommand
    data object FinishDay : EngineCommand
}

sealed interface EngineResult {
    data class Applied(val state: GameState) : EngineResult
    data class Blocked(val reason: BlockReason) : EngineResult
}

sealed interface BlockReason {
    data object InvalidPetName : BlockReason
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
    data object StoryConditionsNotMet : BlockReason
    data object ChapterGoalIncomplete : BlockReason
    data object GoalUnavailable : BlockReason
    data object GoalAlreadySelected : BlockReason
    data object ItemAlreadyOwned : BlockReason
    data class FoodBudgetWarning(val remainingBalance: Long, val neededForFood: Long) : BlockReason
    data class MissingItems(val itemIds: Set<String>) : BlockReason
    data class InsufficientMoney(val missing: Long) : BlockReason
    data class InvalidContent(val detail: String) : BlockReason
}
