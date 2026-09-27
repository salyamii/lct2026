package ru.nksk.lctapp.feature.economy.ui

internal enum class SavingsStep { DEPOSIT, WITHDRAW }

/** Back only dismisses an uncommitted confirmation; ordinary editing exits this destination. */
internal fun savingsBackAction(state: EconomyUiState): EconomyAction? = when {
    state.withdrawal != null -> EconomyAction.CancelWithdrawal
    state.depositWarning != null -> EconomyAction.CancelDepositRisk
    else -> null
}

internal data class SavingsTargetUi(val id: String, val name: String, val price: Long) {
    fun remaining(savings: Long): Long = (price - savings).coerceAtLeast(0)
}

/** A displayed proposal only. It cannot move money without the separate confirmation action. */
internal data class WithdrawalPreview(
    val amount: Long,
    val available: Long,
    val savings: Long,
    val revision: Long?,
    val target: SavingsTargetUi?,
    val knownNeeds: Long,
) {
    val availableAfter: Long get() = available + amount
    val savingsAfter: Long get() = savings - amount
}

/** Created only from an Applied command, never from entering or closing a screen. */
internal data class SavingsReceipt(
    val amount: Long,
    val withdrawing: Boolean,
    val availableBefore: Long,
    val savingsBefore: Long,
    val availableAfter: Long,
    val savingsAfter: Long,
)
