package ru.nksk.lctapp.domain.economy

/**
 * Financial snapshot. Amounts use integral virtual currency units.
 * Callers supply values; this model defines no starting balance, prices, rewards,
 * or arithmetic relationship between balance, allocations, savings, and reserve.
 */
data class EconomyState(
    val balance: Long,
    val budgetAllocations: List<BudgetAllocation>,
    val savingsGoal: SavingsGoal?,
    val reserve: Long,
    val expenses: List<Expense>,
)

data class BudgetAllocation(
    val categoryId: String,
    val amount: Long,
)

data class SavingsGoal(
    val id: String,
    val targetAmount: Long,
    val savedAmount: Long,
)

data class Expense(
    val id: String,
    val amount: Long,
)
