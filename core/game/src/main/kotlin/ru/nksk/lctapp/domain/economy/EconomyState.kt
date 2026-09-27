package ru.nksk.lctapp.domain.economy

import kotlinx.serialization.Serializable

/** Current allocations cover available money; confirmed intentions live in FinancialProgress.plans. */
@Serializable
data class EconomyState(
    val plan: BudgetPlan,
    val unallocated: Long = 0,
    val planning: BudgetPlanning? = null,
    val availableBalance: Long = Math.addExact(plan.total, unallocated),
    val savingsBalance: Long = 0,
) {
    init { require(unallocated >= 0 && availableBalance >= 0 && savingsBalance >= 0); balance }
    val balance: Long get() = Math.addExact(availableBalance, savingsBalance)
    val displayPlan: BudgetPlan get() = planning?.draft ?: plan
}

@Serializable
data class BudgetPlan(val needs: Long, val wants: Long, val savings: Long, val reserve: Long) {
    init { require(listOf(needs, wants, savings, reserve).all { it >= 0 }); total }
    val total: Long get() = Math.addExact(Math.addExact(needs, wants), Math.addExact(savings, reserve))
    fun amount(section: BudgetSection): Long = when (section) {
        BudgetSection.NEEDS -> needs; BudgetSection.WANTS -> wants
        BudgetSection.SAVINGS -> savings; BudgetSection.RESERVE -> reserve
    }
    fun withAmount(section: BudgetSection, amount: Long): BudgetPlan = when (section) {
        BudgetSection.NEEDS -> copy(needs = amount); BudgetSection.WANTS -> copy(wants = amount)
        BudgetSection.SAVINGS -> copy(savings = amount); BudgetSection.RESERVE -> copy(reserve = amount)
    }
}
@Serializable
enum class BudgetSection { NEEDS, WANTS, SAVINGS, RESERVE }
@Serializable
enum class BudgetPlanningReason { INITIAL, WEEKLY, MIGRATION, MANUAL }
@Serializable
enum class BudgetPlanningStage { RECEIPT, ALLOCATION }
@Serializable
enum class BudgetRevisionReason { INITIAL, KNOWN_NEED_OMITTED, UNEXPECTED_EXPENSE, NEW_INCOME, CHANGED_PRIORITY, UNSPECIFIED }
@Serializable
data class BudgetPlanning(val id: String, val reason: BudgetPlanningReason, val stage: BudgetPlanningStage,
    val income: Long, val revision: Long = 0, val draft: BudgetPlan? = null, val baseAmount: Long? = null) {
    init { require(id.isNotBlank() && income >= 0 && revision >= 0 && (baseAmount == null || baseAmount >= 0)) }
}
