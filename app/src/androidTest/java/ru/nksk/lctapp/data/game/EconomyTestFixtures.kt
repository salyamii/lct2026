package ru.nksk.lctapp.data.game

import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState

/** Storage tests that replace a total explicitly put the fixture money into Reserve. */
internal fun EconomyState.withTotalBalance(amount: Long) =
    EconomyState(plan = BudgetPlan(0, 0, 0, amount), unallocated = 0, planning = null)
