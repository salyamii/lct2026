package ru.nksk.lctapp.domain.economy

/** Allocated balances and money awaiting mandatory planning form the only wallet. */
data class EconomyState(val plan: BudgetPlan, val unallocated: Long = 0, val planning: BudgetPlanning? = null) {
    init { require(unallocated >= 0); balance }
    val balance: Long get() = Math.addExact(plan.total, unallocated)
}

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
enum class BudgetSection { NEEDS, WANTS, SAVINGS, RESERVE }
enum class BudgetPlanningReason { INITIAL, WEEKLY, MIGRATION, MANUAL }
enum class BudgetPlanningStage { RECEIPT, ALLOCATION }
data class BudgetPlanning(val id: String, val reason: BudgetPlanningReason, val stage: BudgetPlanningStage,
    val income: Long, val revision: Long = 0) {
    init { require(id.isNotBlank() && income >= 0 && revision >= 0) }
}
