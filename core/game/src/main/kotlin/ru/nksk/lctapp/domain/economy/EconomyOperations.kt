package ru.nksk.lctapp.domain.economy

import ru.nksk.lctapp.domain.content.EventType

enum class SpendingKind { GENERAL, WANT, FEEDING, GOAL, EARNING;
    companion object { fun forEvent(type: EventType) = when(type) {
        EventType.WANT -> WANT; EventType.EARNING -> EARNING; else -> GENERAL
    } }
}
data class SpendPart(val section: BudgetSection, val amount: Long)
data class SpendingQuote(val parts: List<SpendPart>, val missing: Long, val blocked: Boolean = false) {
    val affordable: Boolean get() = missing == 0L && !blocked
}
enum class EconomyFailure { STALE_SESSION, INVALID_ALLOCATION, PLANNING_REQUIRED, FORBIDDEN_COST, INSUFFICIENT_MONEY }
class EconomyViolation(val reason: EconomyFailure, val missing: Long = 0) : IllegalArgumentException(reason.name)

/** Pure rules; callers persist the resulting aggregate within their latest-state transaction. */
object EconomyOperations {
    fun beginManual(state: EconomyState, id: String): EconomyState {
        if (state.planning != null) fail(EconomyFailure.STALE_SESSION)
        return state.copy(planning = BudgetPlanning(id, BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION, 0))
    }
    fun minimumNeeds(state: EconomyState): Long =
        if (state.planning == null || state.planning.reason == BudgetPlanningReason.MANUAL) 0 else 35

    private fun fail(reason: EconomyFailure): Nothing = throw EconomyViolation(reason)
    private fun session(state: EconomyState, id: String, revision: Long): BudgetPlanning =
        state.planning?.takeIf { it.id == id && it.revision == revision } ?: fail(EconomyFailure.STALE_SESSION)
    private fun changed(state: EconomyState, session: BudgetPlanning) = state.copy(planning = session.copy(revision = Math.addExact(session.revision, 1)))
    fun startAllocation(state: EconomyState, id: String, revision: Long): EconomyState {
        val session = session(state, id, revision)
        if (session.stage != BudgetPlanningStage.RECEIPT) fail(EconomyFailure.STALE_SESSION)
        return changed(state, session.copy(stage = BudgetPlanningStage.ALLOCATION))
    }
    fun setAllocation(state: EconomyState, id: String, revision: Long, section: BudgetSection, amount: Long): EconomyState {
        if (section == BudgetSection.NEEDS && amount < minimumNeeds(state)) fail(EconomyFailure.INVALID_ALLOCATION)
        return allocate(state, id, revision, section, amount)
    }
    private fun allocate(state: EconomyState, id: String, revision: Long, section: BudgetSection, amount: Long): EconomyState {
        val session = session(state, id, revision)
        if (session.stage != BudgetPlanningStage.ALLOCATION) fail(EconomyFailure.STALE_SESSION)
        val previous = state.plan.amount(section)
        if (amount < 0 || amount > Math.addExact(previous, state.unallocated) ||
            (section == BudgetSection.NEEDS && amount < minimumNeeds(state) && amount <= previous)) fail(EconomyFailure.INVALID_ALLOCATION)
        return changed(state.copy(plan = state.plan.withAmount(section, amount),
            unallocated = Math.subtractExact(Math.addExact(state.unallocated, previous), amount)), session)
    }
    fun adjustAllocation(state: EconomyState, id: String, revision: Long, section: BudgetSection, increase: Boolean): EconomyState {
        val old = state.plan.amount(section)
        val amount = if (increase) Math.addExact(old, minOf(5, state.unallocated)) else old - 5
        return allocate(state, id, revision, section, amount)
    }
    fun confirm(state: EconomyState, id: String, revision: Long): EconomyState {
        val session = session(state, id, revision)
        if (session.stage != BudgetPlanningStage.ALLOCATION || state.unallocated != 0L || state.plan.needs < minimumNeeds(state))
            fail(EconomyFailure.INVALID_ALLOCATION)
        return state.copy(planning = null)
    }
    fun weekly(state: EconomyState, id: String, income: Long): EconomyState {
        if (state.planning != null) fail(EconomyFailure.PLANNING_REQUIRED)
        require(income >= 0)
        return state.copy(unallocated = Math.addExact(state.unallocated, income),
            planning = BudgetPlanning(id, BudgetPlanningReason.WEEKLY, BudgetPlanningStage.RECEIPT, income))
    }
    fun earn(state: EconomyState, income: Long): EconomyState {
        require(income >= 0)
        if (state.planning != null || state.unallocated != 0L) fail(EconomyFailure.PLANNING_REQUIRED)
        return state.copy(plan = state.plan.copy(reserve = Math.addExact(state.plan.reserve, income)))
    }
    fun quote(state: EconomyState, amount: Long, kind: SpendingKind): SpendingQuote {
        require(amount >= 0)
        if (state.planning != null || state.unallocated != 0L || (kind == SpendingKind.EARNING && amount > 0))
            return SpendingQuote(emptyList(), amount, blocked = true)
        val order = when (kind) {
            SpendingKind.GENERAL -> listOf(BudgetSection.RESERVE, BudgetSection.WANTS, BudgetSection.SAVINGS, BudgetSection.NEEDS)
            SpendingKind.WANT -> listOf(BudgetSection.WANTS, BudgetSection.RESERVE, BudgetSection.SAVINGS, BudgetSection.NEEDS)
            SpendingKind.FEEDING -> listOf(BudgetSection.NEEDS, BudgetSection.RESERVE, BudgetSection.WANTS, BudgetSection.SAVINGS)
            SpendingKind.GOAL -> listOf(BudgetSection.SAVINGS)
            SpendingKind.EARNING -> emptyList()
        }
        var remaining = amount
        val parts = order.mapNotNull { section ->
            val take = minOf(state.plan.amount(section), remaining)
            remaining -= take
            if (take > 0) SpendPart(section, take) else null
        }
        return SpendingQuote(parts, remaining)
    }
    fun spend(state: EconomyState, amount: Long, kind: SpendingKind): EconomyState {
        if (state.planning != null || state.unallocated != 0L) fail(EconomyFailure.PLANNING_REQUIRED)
        if (kind == SpendingKind.EARNING && amount > 0) fail(EconomyFailure.FORBIDDEN_COST)
        val quote = quote(state, amount, kind)
        if (!quote.affordable) throw EconomyViolation(EconomyFailure.INSUFFICIENT_MONEY, quote.missing)
        return state.copy(plan = quote.parts.fold(state.plan) { plan, part ->
            plan.withAmount(part.section, plan.amount(part.section) - part.amount)
        })
    }
}
