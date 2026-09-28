package ru.nksk.lctapp.feature.goal.ui

import ru.nksk.lctapp.domain.economy.SpendPart

internal data class GoalUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val busy: Boolean = false,
    val title: String = "",
    val petName: String = "",
    val scene: String? = "observatory",
    val character: Int? = null,
    val description: String = "",
    val selected: Boolean = false,
    val balance: Long = 0,
    val availableBalance: Long = 0,
    val knownNeeds: Long = 0,
    val transfersEnabled: Boolean = false,
    val contextId: String? = null,
    val totalPrice: Long = 0,
    val remainingPrice: Long = 0,
    val collected: Int = 0,
    val parts: List<GoalPartUiState> = emptyList(),
    val storyHint: String = "",
    val message: String? = null,
    val confirmation: PurchaseConfirmation? = null,
    val celebration: String? = null,
    val purchaseResult: GoalPurchaseResult? = null,
    val projects: List<GoalProjectUiState> = emptyList(),
    val showList: Boolean = false,
    val returnToList: Boolean = false,
    val goalId: String? = null,
    val canSelect: Boolean = false,
    val completedProject: Boolean = false,
    val completedProjectCount: Int = 0,
    val campaignComplete: Boolean = false,
)

internal enum class GoalProjectStatus { AVAILABLE, ACTIVE, LOCKED, COMPLETED }
internal data class GoalProjectUiState(
    val id: String, val title: String, val description: String, val price: Long,
    val parts: Int, val bought: Int, val status: GoalProjectStatus, val hint: String,
    val requirements: List<GoalRequirementUiState>,
)

internal data class GoalRequirementUiState(
    val id: String, val title: String, val price: Long, val owned: Boolean,
)

internal data class GoalPartUiState(
    val id: String, val title: String, val description: String, val price: Long,
    val owned: Boolean, val canBuy: Boolean, val blockedMessage: String?,
    val missingCoins: Long? = null,
    val savingTarget: Boolean = false,
    val canSelect: Boolean = false,
    val savedCoins: Long = 0,
    val remainingCoins: Long = 0,
    val availableContribution: Long = 0,
)

internal data class PurchaseConfirmation(
    val itemId: String,
    val itemTitle: String,
    val price: Long,
    val fromSavings: Long,
    val availableParts: List<SpendPart>,
    val availableBefore: Long,
    val savingsBefore: Long,
    val remainingBalance: Long,
    val remainingSavings: Long,
    val foodNeeded: Long,
    val contextId: String,
) {
    val foodShortfall: Long get() = (foodNeeded - remainingBalance).coerceAtLeast(0)
}

internal data class GoalPurchaseResult(val itemId: String, val itemTitle: String, val price: Long)

internal enum class GoalContinuationDestination { DAY, BUDGET, TRAINING }

internal sealed interface GoalAction {
    data class ContextPresented(val id: String) : GoalAction
    data object Retry : GoalAction
    data class View(val goalId: String) : GoalAction
    data object ShowList : GoalAction
    data object DismissPurchaseResult : GoalAction
    data object ContinueStory : GoalAction
    data class Select(val goalId: String) : GoalAction
    data class SelectSavingGoal(val goalId: String, val itemId: String) : GoalAction
    data class Buy(val goalId: String, val itemId: String) : GoalAction
    data object ConfirmPurchase : GoalAction
    data object CancelPurchase : GoalAction
}
