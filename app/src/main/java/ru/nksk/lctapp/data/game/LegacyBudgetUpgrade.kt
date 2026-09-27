package ru.nksk.lctapp.data.game

import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState

/** A legacy intention does not reveal the remaining envelopes. The original belongs in the audit before-checkpoint. */
internal fun upgradeLegacyBudget(state: GameState, sessionId: String): GameState {
    val economy = state.economy
    if (economy.planning != null && economy.hasValidLiveBudget()) return state
    val available = economy.availableBalance
    val empty = BudgetPlan(0, 0, 0, 0)
    return state.copy(economy = economy.copy(plan = empty, unallocated = available,
        planning = if (available == 0L) null else BudgetPlanning(sessionId,
            BudgetPlanningReason.MIGRATION, BudgetPlanningStage.ALLOCATION, income = 0,
            draft = empty, baseAmount = available)))
}

/** Validate only the imported current snapshot; old audit checkpoints must retain their legacy interpretation. */
internal fun EconomyState.hasValidLiveBudget(): Boolean {
    val active = planning ?: return unallocated == 0L && plan.total == availableBalance
    // RECEIPT legitimately precedes creation of an explicit draft, including the first 100 coins.
    val draft = active.draft ?: plan
    val basis = active.baseAmount ?: availableBalance
    return basis == availableBalance && draft.total <= basis && unallocated == basis - draft.total
}
