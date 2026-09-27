package ru.nksk.lctapp.domain.economy

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.content.EventType

@Serializable
enum class SpendingKind { GENERAL, WANT, FEEDING, GOAL, EARNING;
    companion object { fun forEvent(type: EventType) = when(type) {
        EventType.WANT -> WANT; EventType.EARNING -> EARNING; else -> GENERAL
    } }
}
/** A portion of available money taken from a current allocation; it is never a bank withdrawal. */
@Serializable
data class SpendPart(val section: BudgetSection, val amount: Long)
@Serializable
data class SpendingQuote(val parts: List<SpendPart>, val missing: Long, val blocked: Boolean = false) {
    val affordable: Boolean get() = missing == 0L && !blocked
}
@Serializable
enum class EconomyFailure {
    STALE_SESSION, INVALID_ALLOCATION, PLANNING_REQUIRED, FORBIDDEN_COST,
    INSUFFICIENT_MONEY, CONFIRMATION_REQUIRED,
}
class EconomyViolation(val reason: EconomyFailure, val missing: Long = 0) : IllegalArgumentException(reason.name)

/** Pure operations. The engine records facts and commits the complete aggregate. */
object EconomyOperations {
    /** Editing starts with the current remaining allocations, never a reset of spent money. */
    fun proposedPlan(state: EconomyState): BudgetPlan = state.plan

    fun planningAmount(state: EconomyState): Long = state.planning?.baseAmount ?: state.availableBalance
    fun allocationRemaining(state: EconomyState): Long =
        Math.subtractExact(planningAmount(state), if (state.planning != null) state.displayPlan.total else proposedPlan(state).total)

    fun beginManual(state: EconomyState, id: String): EconomyState {
        if (state.planning != null) fail(EconomyFailure.STALE_SESSION)
        requireReady(state)
        val draft = proposedPlan(state)
        return state.copy(unallocated = state.availableBalance - draft.total,
            planning = BudgetPlanning(id, BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION, 0,
                draft = draft, baseAmount = state.availableBalance))
    }

    /** A revised plan covers food still needed; a fresh income period starts with its full week. */
    fun minimumNeeds(state: EconomyState, knownNeeds: Long = 35L): Long {
        require(knownNeeds >= 0)
        val needed = when (state.planning?.reason) {
            BudgetPlanningReason.INITIAL, BudgetPlanningReason.WEEKLY -> 35L
            BudgetPlanningReason.MANUAL, BudgetPlanningReason.MIGRATION, null -> knownNeeds
        }
        return minOf(needed, planningAmount(state))
    }

    private fun fail(reason: EconomyFailure): Nothing = throw EconomyViolation(reason)
    private fun session(state: EconomyState, id: String, revision: Long): BudgetPlanning =
        state.planning?.takeIf { it.id == id && it.revision == revision } ?: fail(EconomyFailure.STALE_SESSION)
    private fun changed(state: EconomyState, session: BudgetPlanning) =
        state.copy(planning = session.copy(revision = Math.addExact(session.revision, 1)))

    fun startAllocation(state: EconomyState, id: String, revision: Long): EconomyState {
        val session = session(state, id, revision)
        if (session.stage != BudgetPlanningStage.RECEIPT) fail(EconomyFailure.STALE_SESSION)
        val basis = session.baseAmount ?: state.availableBalance
        val draft = session.draft ?: state.plan
        if (draft.total > basis) fail(EconomyFailure.INVALID_ALLOCATION)
        return changed(state.copy(unallocated = basis - draft.total),
            session.copy(stage = BudgetPlanningStage.ALLOCATION, draft = draft, baseAmount = basis))
    }

    fun setAllocation(state: EconomyState, id: String, revision: Long, section: BudgetSection, amount: Long,
        knownNeeds: Long = 35L): EconomyState {
        if (section == BudgetSection.NEEDS && amount < minimumNeeds(state, knownNeeds)) fail(EconomyFailure.INVALID_ALLOCATION)
        return allocate(state, id, revision, section, amount, knownNeeds)
    }

    private fun allocate(state: EconomyState, id: String, revision: Long, section: BudgetSection, amount: Long,
        knownNeeds: Long): EconomyState {
        val session = session(state, id, revision)
        if (session.stage != BudgetPlanningStage.ALLOCATION) fail(EconomyFailure.STALE_SESSION)
        val draft = state.displayPlan
        val previous = draft.amount(section)
        val remaining = allocationRemaining(state)
        if (amount < 0 || amount > Math.addExact(previous, remaining) ||
            (section == BudgetSection.NEEDS && amount < minimumNeeds(state, knownNeeds) && amount <= previous))
            fail(EconomyFailure.INVALID_ALLOCATION)
        val next = draft.withAmount(section, amount)
        return changed(state.copy(unallocated = planningAmount(state) - next.total),
            session.copy(draft = next, baseAmount = planningAmount(state)))
    }

    fun adjustAllocation(state: EconomyState, id: String, revision: Long, section: BudgetSection, increase: Boolean,
        knownNeeds: Long = 35L): EconomyState {
        val old = state.displayPlan.amount(section)
        val amount = if (increase) Math.addExact(old, minOf(5, allocationRemaining(state))) else old - 5
        return allocate(state, id, revision, section, amount, knownNeeds)
    }

    fun confirm(state: EconomyState, id: String, revision: Long, knownNeeds: Long = 35L): EconomyState {
        val session = session(state, id, revision)
        if (session.stage != BudgetPlanningStage.ALLOCATION || allocationRemaining(state) != 0L ||
            state.displayPlan.needs < minimumNeeds(state, knownNeeds)) fail(EconomyFailure.INVALID_ALLOCATION)
        return state.copy(plan = state.displayPlan, planning = null, unallocated = 0)
    }

    fun weekly(state: EconomyState, id: String, income: Long): EconomyState {
        requireReady(state)
        require(income >= 0)
        val amount = Math.addExact(state.availableBalance, income)
        return state.copy(availableBalance = amount, unallocated = income,
            planning = BudgetPlanning(id, BudgetPlanningReason.WEEKLY, BudgetPlanningStage.RECEIPT, income,
                draft = state.plan, baseAmount = amount))
    }

    fun earn(state: EconomyState, income: Long): EconomyState {
        require(income >= 0)
        requireReady(state)
        return state.copy(availableBalance = Math.addExact(state.availableBalance, income),
            plan = state.plan.copy(reserve = Math.addExact(state.plan.reserve, income)))
    }

    fun quote(state: EconomyState, amount: Long, kind: SpendingKind): SpendingQuote {
        require(amount >= 0)
        if (state.planning != null || state.unallocated != 0L || state.plan.total != state.availableBalance ||
            (kind == SpendingKind.EARNING && amount > 0))
            return SpendingQuote(emptyList(), amount, blocked = true)
        if (kind == SpendingKind.GOAL) {
            val take = minOf(state.savingsBalance, amount)
            return SpendingQuote(if (take == 0L) emptyList() else listOf(SpendPart(BudgetSection.SAVINGS, take)), amount - take)
        }
        return quoteAvailable(state.plan, amount, when (kind) {
            SpendingKind.FEEDING -> listOf(BudgetSection.NEEDS, BudgetSection.RESERVE, BudgetSection.WANTS, BudgetSection.SAVINGS)
            SpendingKind.WANT -> listOf(BudgetSection.WANTS, BudgetSection.RESERVE, BudgetSection.SAVINGS, BudgetSection.NEEDS)
            else -> listOf(BudgetSection.RESERVE, BudgetSection.WANTS, BudgetSection.SAVINGS, BudgetSection.NEEDS)
        })
    }

    fun spend(state: EconomyState, amount: Long, kind: SpendingKind): EconomyState {
        requireReady(state)
        if (kind == SpendingKind.EARNING && amount > 0) fail(EconomyFailure.FORBIDDEN_COST)
        val quote = quote(state, amount, kind)
        if (!quote.affordable) throw EconomyViolation(EconomyFailure.INSUFFICIENT_MONEY, quote.missing)
        return if (kind == SpendingKind.GOAL) state.copy(savingsBalance = state.savingsBalance - amount)
        else state.copy(availableBalance = state.availableBalance - amount, plan = deduct(state.plan, quote.parts))
    }

    fun depositQuote(state: EconomyState, amount: Long): SpendingQuote {
        require(amount >= 0)
        if (state.planning != null || state.unallocated != 0L || state.plan.total != state.availableBalance)
            return SpendingQuote(emptyList(), amount, blocked = true)
        return quoteAvailable(state.plan, amount,
            listOf(BudgetSection.SAVINGS, BudgetSection.RESERVE, BudgetSection.WANTS, BudgetSection.NEEDS))
    }

    fun deposit(state: EconomyState, amount: Long): EconomyState {
        require(amount > 0)
        requireReady(state)
        val quote = depositQuote(state, amount)
        if (!quote.affordable) throw EconomyViolation(EconomyFailure.INSUFFICIENT_MONEY, quote.missing)
        return state.copy(availableBalance = state.availableBalance - amount, plan = deduct(state.plan, quote.parts),
            savingsBalance = Math.addExact(state.savingsBalance, amount))
    }

    fun withdraw(state: EconomyState, amount: Long, confirmed: Boolean): EconomyState {
        require(amount > 0)
        requireReady(state)
        if (!confirmed) fail(EconomyFailure.CONFIRMATION_REQUIRED)
        if (amount > state.savingsBalance) throw EconomyViolation(EconomyFailure.INSUFFICIENT_MONEY, amount - state.savingsBalance)
        return state.copy(availableBalance = Math.addExact(state.availableBalance, amount),
            savingsBalance = state.savingsBalance - amount,
            plan = state.plan.copy(reserve = Math.addExact(state.plan.reserve, amount)))
    }

    private fun quoteAvailable(plan: BudgetPlan, amount: Long, order: List<BudgetSection>): SpendingQuote {
        var remaining = amount
        val parts = buildList {
            for (section in order) {
                val take = minOf(plan.amount(section), remaining)
                if (take > 0) add(SpendPart(section, take))
                remaining -= take
            }
        }
        return SpendingQuote(parts, remaining)
    }

    private fun deduct(plan: BudgetPlan, parts: List<SpendPart>): BudgetPlan = parts.fold(plan) { remaining, part ->
        remaining.withAmount(part.section, remaining.amount(part.section) - part.amount)
    }

    private fun requireReady(state: EconomyState) {
        if (state.planning != null || state.unallocated != 0L) fail(EconomyFailure.PLANNING_REQUIRED)
        if (state.plan.total != state.availableBalance) fail(EconomyFailure.INVALID_ALLOCATION)
    }
}
