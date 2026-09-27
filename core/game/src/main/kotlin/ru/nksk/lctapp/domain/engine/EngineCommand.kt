package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.pet.PetColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind

/** Revision is captured with the UI state. A stale command never changes a newer save. */
@Serializable
data class EngineRequest(val id: String, val expectedRevision: Long?, val command: EngineCommand,
    val context: DecisionContext? = null) {
    init { require(id.isNotBlank()) }
}

/** Displayed transfer context. A null target is meaningful: no item was selected. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SavingsTransferExpectation(
    val availableBalance: Long,
    val savingsBalance: Long,
    val selectedSavingItemId: String?,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val allocation: BudgetPlan? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed interface EngineCommand {
    @Serializable
    data class StartBudgetAllocation(val sessionId: String, val revision: Long) : EngineCommand
    @Serializable
    data class ChangeBudgetAllocation(val sessionId: String, val revision: Long, val section: BudgetSection,
        val amount: Long? = null, val increase: Boolean? = null, val startManual: Boolean = false) : EngineCommand {
        init { require((amount == null) != (increase == null)) }
    }
    @Serializable
    data class ConfirmBudget(val sessionId: String, val revision: Long,
        val reason: BudgetRevisionReason = BudgetRevisionReason.UNSPECIFIED,
        val causeActionId: String? = null) : EngineCommand
    @Serializable
    data class DepositSavings(val amount: Long, val acceptFoodRisk: Boolean = false,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val expected: SavingsTransferExpectation? = null) : EngineCommand
    @Serializable
    data class WithdrawSavings(val amount: Long, val confirmed: Boolean,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val expected: SavingsTransferExpectation? = null) : EngineCommand
    @Serializable
    data class RequestFinancialPractice(val kind: FinancialQuestionKind = FinancialQuestionKind.PLAN_REVIEW,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val series: Boolean = false) : EngineCommand
    @Serializable
    data class AnswerFinancialQuestion(val questionId: String, val answerId: String, val usedHint: Boolean = false) : EngineCommand
    @Serializable
    data class AdvanceFinancialPractice(val questionId: String) : EngineCommand
    @Serializable
    data object CloseFinancialPractice : EngineCommand
    @Serializable
    data class RenamePet(val name: String, val expectedName: String) : EngineCommand
    @Serializable
    data class SetPetColor(val color: PetColor, val expectedColor: PetColor) : EngineCommand
    @Serializable
    data class SetPetLook(val lookId: String, val expectedLookId: String) : EngineCommand
    /** Explicit authored day plan, including the event IDs of any carried lore. */
    @Serializable data class BeginDay(val storyDayId: String, val eventIds: List<String>, val openFirst: Boolean = false) : EngineCommand
    @Serializable data object OpenNextEvent : EngineCommand
    @Serializable data class SelectGoal(val goalId: String, val firstDay: BeginDay? = null) : EngineCommand
    @Serializable data class SelectSavingGoal(val goalId: String, val itemId: String, val firstDay: BeginDay? = null) : EngineCommand
    @Serializable data class BuyGoalItem(val goalId: String, val itemId: String, val acceptFoodRisk: Boolean = false) : EngineCommand
    @Serializable data class Choose(val occurrenceId: String, val choiceId: String) : EngineCommand
    /** Commit a choice and close its result atomically when no authored result screen is needed. */
    @Serializable data class CompleteEvent(val occurrenceId: String, val choiceId: String, val priorityId: String? = null,
        val resourcePriorityOfferId: String? = null) : EngineCommand
    @Serializable data class AcknowledgeResult(val occurrenceId: String) : EngineCommand
    @Serializable data class StartDeed(val offerId: String, val priorityId: String? = null) : EngineCommand
    @Serializable data class AcceptDeedProposal(val occurrenceId: String, val priorityId: String? = null) : EngineCommand
    @Serializable data class CompleteDeed(val occurrenceId: String, val score: DeedGameScore) : EngineCommand
    /** Validate admission without applying the chosen story action. */
    @Serializable data class StartStoryGame(val occurrenceId: String, val choiceId: String,
        val resourcePriorityOfferId: String? = null) : EngineCommand
    /** Also used for manual RANDOM repairs; wire identity is kept stable. */
    @Serializable data class CompleteStoryGame(val occurrenceId: String, val choiceId: String, val score: DeedGameScore,
        val resourcePriorityOfferId: String? = null) : EngineCommand
    @Serializable data class DismissDeedProposal(val occurrenceId: String) : EngineCommand
    /** Leave without recording a choice, spending effort or completing the story. */
    @Serializable data class PauseEvent(val occurrenceId: String) : EngineCommand
    @Serializable data class Feed(val mealId: String) : EngineCommand
    /** Defer the blocked card and finish the day in a single transaction. */
    @Serializable data class FinishDayFromEvent(val occurrenceId: String) : EngineCommand
    @Serializable data object FinishDay : EngineCommand
}

sealed interface EngineResult {
    data class Applied(val state: GameState) : EngineResult
    data class Blocked(val reason: BlockReason) : EngineResult
}

sealed interface BlockReason {
    data class FinancialPracticeRequired(val missing: List<ru.nksk.lctapp.domain.finance.FinancialMilestone>) : BlockReason
    data object SavingsWithdrawalConfirmationRequired : BlockReason
    data object BudgetPlanningRequired : BlockReason
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
