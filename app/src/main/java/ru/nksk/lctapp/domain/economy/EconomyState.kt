package ru.nksk.lctapp.domain.economy

/** One actual wallet. Plan sections are independent allocations, not extra balances. */
data class EconomyState(val balance: Long, val plan: BudgetPlan)

/** Integral plan values; no sum, range, period or spending limit is imposed here. */
data class BudgetPlan(val needs: Long, val wants: Long, val savings: Long, val reserve: Long)

enum class BudgetSection { NEEDS, WANTS, SAVINGS, RESERVE }
