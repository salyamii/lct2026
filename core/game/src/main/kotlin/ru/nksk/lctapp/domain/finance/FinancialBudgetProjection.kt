package ru.nksk.lctapp.domain.finance

import kotlinx.serialization.Serializable
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

/** Transfers and goal purchases are different facts; spending saved money does not undo its deposit. */
@Serializable
data class BudgetActuals(
    val needs: Long = 0,
    val wants: Long = 0,
    val reserve: Long = 0,
    val unknownExpenses: Long = 0,
    val deposited: Long = 0,
    val withdrawn: Long = 0,
    val goalPurchases: Long = 0,
    val income: Long = 0,
    /** A known goal expense from the wallet; neither a transfer nor spending real savings. */
    val goalPurchasesAvailable: Long = 0,
) {
    init { require(listOf(needs, wants, reserve, unknownExpenses, deposited, withdrawn, goalPurchases, income, goalPurchasesAvailable).all { it >= 0 }) }
    val netSaved: Long get() = Math.subtractExact(deposited, withdrawn)
    val availableExpenses: Long get() = Math.addExact(goalPurchasesAvailable,
        Math.addExact(Math.addExact(needs, wants), Math.addExact(reserve, unknownExpenses)))
    fun amount(section: BudgetSection): Long = when (section) {
        BudgetSection.NEEDS -> needs
        BudgetSection.WANTS -> wants
        BudgetSection.RESERVE -> reserve
        BudgetSection.SAVINGS -> netSaved
    }
}

/** A revision is a plan for the remaining money, not a replacement of earlier expenses. */
@Serializable
data class BudgetPlanComparison(
    val revision: BudgetPlanRevision,
    val actual: BudgetActuals,
    val complete: Boolean,
    val finalised: Boolean,
    val nextRevisionId: String? = null,
    val sourceActionIds: List<String> = emptyList(),
    val nextRevisionReason: BudgetRevisionReason? = null,
) {
    /** A factual local condition, not a negative label for adapting after an unexpected expense. */
    val managedPlan: Boolean? get() = when {
        !complete -> null
        actual.needs > revision.allocation.needs || actual.wants > revision.allocation.wants -> false
        finalised && actual.netSaved < revision.allocation.savings ->
            if (nextRevisionReason in setOf(BudgetRevisionReason.NEW_INCOME, BudgetRevisionReason.UNEXPECTED_EXPENSE)) null else false
        else -> true
    }
}

@Serializable
data class PeriodBudgetReport(
    val periodId: String,
    val actual: BudgetActuals,
    val complete: Boolean,
    val imported: Boolean,
    val comparisons: List<BudgetPlanComparison>,
)

/** Rebuildable view of immutable receipts. It never changes a save or guesses missing legacy categories. */
object FinancialBudgetProjection {
    fun report(state: GameState, history: List<AuditEntry>, content: StoryContent): List<PeriodBudgetReport> =
        state.financial.periods.map { period -> report(period, state.financial.plans, history, content) }

    /** A practice question needs one period, without rebuilding all earlier goals' reports. */
    fun reportPeriod(state: GameState, periodId: String?, history: List<AuditEntry>,
        content: StoryContent): PeriodBudgetReport? = state.financial.periods.firstOrNull { it.id == periodId }?.let {
        report(it, state.financial.plans, history, content)
    }

    private fun report(period: FinancialPeriod, plans: List<BudgetPlanRevision>, history: List<AuditEntry>,
        content: StoryContent): PeriodBudgetReport {
        val first = history.firstOrNull { entry -> entry.after?.financial?.periods?.any { it.id == period.id } == true }
        val closing = history.firstOrNull { entry ->
            entry.after?.financial?.periods?.any { it.id == period.id && it.closedDay != null } == true
        }
        val range = if (first == null) emptyList() else history.filter {
            it.sequence >= first.sequence && (closing == null || it.sequence <= closing.sequence)
        }
        val entries = range.filter { it.before?.financial?.currentPeriodId == period.id ||
            it.after?.financial?.currentPeriodId == period.id }
        val tipMatches = range.lastOrNull { it.after != null }?.after?.financial?.periods?.find { it.id == period.id } == period
        val actual = actual(entries, content)
        val totalsMatch = actual.income == period.income && actual.availableExpenses == period.spentAvailable &&
            actual.goalPurchases == period.spentSavings && actual.deposited == period.deposited && actual.withdrawn == period.withdrawn
        val createdHere = first != null && (first.type == AuditType.INITIALIZED ||
            first.before?.financial?.periods?.none { it.id == period.id } == true)
        val complete = !period.imported && createdHere && continuous(range) && tipMatches && totalsMatch && actual.unknownExpenses == 0L
        val revisions = plans.filter { it.periodId == period.id }
        val anchors = revisions.associate { revision -> revision.id to entries.firstOrNull { entry ->
            entry.after?.financial?.plans?.any { it.id == revision.id } == true &&
                entry.before?.financial?.plans?.none { it.id == revision.id } == true
        } }
        val comparisons = revisions.mapIndexed { index, revision ->
            val anchor = anchors[revision.id]
            val next = revisions.getOrNull(index + 1)
            val nextAnchor = next?.let { anchors[it.id] }
            val slice = if (anchor == null) emptyList() else entries.filter {
                it.sequence > anchor.sequence && (nextAnchor == null || it.sequence < nextAnchor.sequence)
            }
            val sliceActual = actual(slice, content)
            // Missing an earlier period checkpoint does not invalidate a later, fully recorded revision.
            val checkpointRange = if (anchor == null) emptyList() else range.filter {
                it.sequence >= anchor.sequence && (nextAnchor == null || it.sequence <= nextAnchor.sequence)
            }
            BudgetPlanComparison(revision, sliceActual,
                complete = anchor != null && (nextAnchor != null || next == null && tipMatches) && continuous(checkpointRange) &&
                    sliceActual.unknownExpenses == 0L,
                finalised = next != null || period.closedDay != null,
                nextRevisionId = next?.id,
                sourceActionIds = slice.mapNotNull { it.request?.id }, nextRevisionReason = next?.reason)
        }
        // Known aggregate totals are still shown when the old detailed history is absent.
        val missingAvailable = (period.spentAvailable - actual.availableExpenses).coerceAtLeast(0)
        val displayed = actual.copy(unknownExpenses = Math.addExact(actual.unknownExpenses, missingAvailable),
            deposited = maxOf(actual.deposited, period.deposited), withdrawn = maxOf(actual.withdrawn, period.withdrawn),
            goalPurchases = maxOf(actual.goalPurchases, period.spentSavings), income = maxOf(actual.income, period.income))
        return PeriodBudgetReport(period.id, displayed, complete, period.imported, comparisons)
    }

    private fun continuous(entries: List<AuditEntry>): Boolean = entries.isNotEmpty() &&
        entries.zipWithNext().all { (a, b) -> a.runId == b.runId && b.sequence == a.sequence + 1 } &&
        entries.none { entry -> entry.type == AuditType.IMPORTED_BASELINE || entry.type == AuditType.RESTORED ||
            (entry.type == AuditType.TECHNICAL_UPDATE && entry.before?.economy != entry.after?.economy) }

    private fun actual(entries: List<AuditEntry>, content: StoryContent): BudgetActuals {
        var result = BudgetActuals()
        val seen = mutableSetOf<String>()
        entries.forEach { entry -> entry.operations.forEach { operation ->
            require(seen.add(operation.operationId)) { "Repeated financial receipt in budget history" }
            fun add(previous: Long) = Math.addExact(previous, operation.amount)
            result = when (operation.kind) {
                LedgerKind.INCOME -> result.copy(income = add(result.income))
                LedgerKind.DEPOSIT -> result.copy(deposited = add(result.deposited))
                LedgerKind.WITHDRAWAL -> result.copy(withdrawn = add(result.withdrawn))
                LedgerKind.SAVINGS_EXPENSE -> result.copy(goalPurchases = add(result.goalPurchases))
                LedgerKind.AVAILABLE_EXPENSE -> if (entry.request?.command is EngineCommand.BuyGoalItem)
                    result.copy(goalPurchasesAvailable = add(result.goalPurchasesAvailable))
                else when (category(entry, operation, content)) {
                    BudgetSection.NEEDS -> result.copy(needs = add(result.needs))
                    BudgetSection.WANTS -> result.copy(wants = add(result.wants))
                    BudgetSection.RESERVE -> result.copy(reserve = add(result.reserve))
                    else -> result.copy(unknownExpenses = add(result.unknownExpenses))
                }
            }
        } }
        return result
    }

    private fun category(entry: AuditEntry, operation: LedgerEntry, content: StoryContent): BudgetSection? {
        val receipt = entry.after?.engine?.journal?.find { it.id == operation.operationId } ?: return null
        val eventId = when (receipt.kind) {
            DayJournalKind.MEAL -> return BudgetSection.NEEDS
            DayJournalKind.EVENT_START -> receipt.sourceId
            DayJournalKind.EVENT_CHOICE, DayJournalKind.DEED -> content.choices.find { it.id == receipt.sourceId }?.eventId
            else -> null
        } ?: return null
        // Matches current SpendingKind/quote semantics, not a hidden withdrawal from four wallets.
        return when (content.events.find { it.id == eventId }?.type) {
            EventType.WANT -> BudgetSection.WANTS
            EventType.STATE, EventType.RANDOM, EventType.STORY -> BudgetSection.RESERVE
            EventType.EARNING, null -> null
        }
    }
}
