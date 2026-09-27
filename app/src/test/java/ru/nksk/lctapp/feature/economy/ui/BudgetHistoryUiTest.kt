package ru.nksk.lctapp.feature.economy.ui

import ru.nksk.lctapp.core.ui.game.BudgetHistoryUi
import ru.nksk.lctapp.core.ui.game.budgetHistoryUi

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.finance.BudgetPlanRevision
import ru.nksk.lctapp.domain.finance.FinancialPeriod
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

class BudgetHistoryUiTest {
    @Test fun explainsBothRealBalancesWithoutCallingEveryDifferenceAnExpense() {
        val ledger = BudgetHistoryFixture()
        ledger.move(LedgerKind.INCOME, 12)
        ledger.move(LedgerKind.AVAILABLE_EXPENSE, 35)
        ledger.move(LedgerKind.DEPOSIT, 40)
        ledger.move(LedgerKind.WITHDRAWAL, 5)
        ledger.move(LedgerKind.SAVINGS_EXPENSE, 6)

        val detail = checkNotNull(budgetHistoryUi(ledger.game, ledger.entries))
        assertEquals(BudgetHistoryUi("plan:1", 90, 12, 12, 35, 40, 5, 6, 32, 41), detail)
        assertEquals(32L, ledger.game.economy.plan.total)
        assertEquals(90L, ledger.game.financial.plans.single().allocation.total)
        assertEquals(73L, ledger.game.economy.balance)
    }

    @Test fun usesTheNewestGlobalConfirmationEvenWhenAllocationsRepeatAndPeriodChanges() {
        val ledger = BudgetHistoryFixture()
        ledger.move(LedgerKind.AVAILABLE_EXPENSE, 10)
        ledger.move(LedgerKind.INCOME, 10)
        ledger.confirmAgain()
        ledger.technical(ledger.game.copy(financial = ledger.game.financial.copy(
            currentPeriodId = "new-period",
            periods = listOf(FinancialPeriod("new-period", "new-goal", 1, 1, 90, 12)),
        )))
        ledger.move(LedgerKind.DEPOSIT, 7)

        assertEquals(BudgetHistoryUi("plan:2", 90, 12, 0, 0, 7, 0, 0, 83, 19),
            budgetHistoryUi(ledger.game, ledger.entries))
    }

    @Test fun missingCurrentAnchorDoesNotFallBackToAnOlderEqualPlanOrAnImportedBalance() {
        val ledger = BudgetHistoryFixture()
        ledger.confirmAgain()
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.filterNot {
            it.type == AuditType.COMMAND && it.after?.financial?.plans?.lastOrNull()?.id == "plan:2"
        }))
        assertNull(budgetHistoryUi(ledger.game, listOf(AuditEntry("import", 1, "run",
            AuditType.IMPORTED_BASELINE, after = ledger.game))))

        val latest = ledger.game.financial.plans.last().copy(id = "different-plan",
            allocation = BudgetPlan(35, 15, 40, 0))
        val inconsistent = ledger.game.copy(financial = ledger.game.financial.copy(plans = ledger.game.financial.plans + latest))
        assertNull(budgetHistoryUi(inconsistent, ledger.entries))
        assertNull(budgetHistoryUi(ledger.game.copy(financial = ledger.game.financial.copy(plans = emptyList())), ledger.entries))
    }

    @Test fun aSequenceGapOrBrokenFullCheckpointCannotProduceACompleteExplanation() {
        val ledger = BudgetHistoryFixture()
        ledger.fact()
        ledger.move(LedgerKind.DEPOSIT, 10)
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.filterNot { it.type == AuditType.FACTS }))

        val last = ledger.entries.last()
        val before = checkNotNull(last.before)
        val wrongBefore = before.copy(fatigue = before.fatigue + 1)
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(before = wrongBefore)))
        assertNull(budgetHistoryUi(ledger.game.copy(fatigue = ledger.game.fatigue + 1), ledger.entries))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1)))
    }

    @Test fun restorationAndUnclassifiedTechnicalMoneyChangesInvalidateTheExplanation() {
        for (type in listOf(AuditType.INITIALIZED, AuditType.IMPORTED_BASELINE, AuditType.RESTORED)) {
            val ledger = BudgetHistoryFixture()
            val boundary = AuditEntry("boundary", ledger.entries.last().sequence + 1, "run", type, after = ledger.game)
            assertNull(budgetHistoryUi(ledger.game, ledger.entries + boundary))
        }
        val ledger = BudgetHistoryFixture()
        ledger.technical(ledger.game.copy(economy = ledger.game.economy.copy(availableBalance = 89)))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries))
    }

    @Test fun nonMonetaryTechnicalChangesFactsAndRejectedAttemptsPreserveTheChain() {
        val ledger = BudgetHistoryFixture()
        ledger.fact()
        ledger.technical(ledger.game.copy(pet = ledger.game.pet.copy(name = "Лис")))
        ledger.entries += AuditEntry("rejected", ledger.entries.last().sequence + 1, "run", AuditType.REJECTED,
            request = EngineRequest("rejected-request", null, EngineCommand.DepositSavings(1_000)))
        ledger.move(LedgerKind.DEPOSIT, 10)
        assertEquals(BudgetHistoryUi("plan:1", 90, 12, 0, 0, 10, 0, 0, 80, 22),
            budgetHistoryUi(ledger.game, ledger.entries))
    }

    @Test fun missingOrMismatchingReceiptsNeverBecomeAnInferredExpense() {
        val ledger = BudgetHistoryFixture()
        ledger.move(LedgerKind.DEPOSIT, 10)
        val last = ledger.entries.last()
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(operations = emptyList())))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(operations = listOf(
            last.operations.single().copy(kind = LedgerKind.AVAILABLE_EXPENSE),
        ))))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(operations = listOf(
            last.operations.single().copy(amount = 9),
        ))))
    }

    @Test fun repeatedReceiptsCommandsOrSequencesAndMixedRunsAreRejected() {
        val ledger = BudgetHistoryFixture()
        ledger.move(LedgerKind.DEPOSIT, 5)
        val first = ledger.entries.last()
        ledger.move(LedgerKind.DEPOSIT, 5)
        val last = ledger.entries.last()
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(
            operations = listOf(last.operations.single().copy(operationId = first.operations.single().operationId)),
        )))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(request = first.request)))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(sequence = first.sequence)))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries.dropLast(1) + last.copy(runId = "another-run")))
        assertNull(budgetHistoryUi(ledger.game, ledger.entries + last))
    }

    @Test fun aRealConfirmationAfterImportCanHaveNoLaterMovement() {
        val ledger = BudgetHistoryFixture()
        // An older imported baseline is not evidence; a later real confirmation supplies a new anchor.
        val baseline = AuditEntry("baseline", 1, "run", AuditType.IMPORTED_BASELINE, after = ledger.entries.first().before)
        val shifted = ledger.entries.map { it.copy(sequence = it.sequence + 1) }
        assertEquals(BudgetHistoryUi("plan:1", 90, 12, 0, 0, 0, 0, 0, 90, 12),
            budgetHistoryUi(ledger.game, listOf(baseline) + shifted))
    }

    @Test fun malformedConfirmationCannotSupplyABaselineEvenWhenItsInventedReceiptsBalance() {
        val ledger = BudgetHistoryFixture()
        val anchor = ledger.entries.single()
        val before = checkNotNull(anchor.before)
        val recordedBoundary = anchor.copy(before = before.copy(economy = before.economy.copy(availableBalance = 85)),
            operations = listOf(LedgerEntry("boundary-income", LedgerKind.INCOME, 5)))
        assertNull(budgetHistoryUi(ledger.game, listOf(recordedBoundary)))
        assertNull(budgetHistoryUi(ledger.game, listOf(recordedBoundary.copy(operations = emptyList()))))

        val mismatchedBasis = ledger.game.copy(financial = ledger.game.financial.copy(
            plans = listOf(ledger.game.financial.plans.single().copy(availableBasis = 85))))
        assertNull(budgetHistoryUi(mismatchedBasis, listOf(anchor.copy(after = mismatchedBasis))))
    }

    @Test fun overflowingTotalsAreUnavailableEvenWhenEveryIndividualBalanceIsValid() {
        val ledger = BudgetHistoryFixture()
        val large = Long.MAX_VALUE - 102
        repeat(2) {
            ledger.move(LedgerKind.INCOME, large)
            ledger.move(LedgerKind.AVAILABLE_EXPENSE, large)
        }
        assertNull(budgetHistoryUi(ledger.game, ledger.entries))
    }
}

/** Ledger/checkpoint fixtures exercise reconciliation; engine transition rules are covered in domain tests. */
private class BudgetHistoryFixture {
    private val allocation = BudgetPlan(35, 20, 35, 0)
    var game: GameState = createInitialGameState().copy(economy = EconomyState(
        plan = BudgetPlan(0, 0, 0, 0), availableBalance = 90, savingsBalance = 12,
        planning = BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.ALLOCATION,
            90, draft = allocation, baseAmount = 90),
    ))
        private set
    val entries = mutableListOf<AuditEntry>()

    init { confirm() }

    fun confirmAgain() {
        technical(game.copy(economy = game.economy.copy(planning = BudgetPlanning("manual",
            BudgetPlanningReason.MANUAL, BudgetPlanningStage.ALLOCATION, 0,
            draft = allocation, baseAmount = game.economy.availableBalance))))
        confirm()
    }

    private fun confirm() {
        val before = game
        val ordinal = before.financial.plans.size + 1
        val plan = BudgetPlanRevision("plan:$ordinal", null, ordinal, 1, before.economy.availableBalance,
            allocation, BudgetRevisionReason.INITIAL)
        game = before.copy(economy = before.economy.copy(plan = allocation, planning = null, unallocated = 0),
            financial = before.financial.copy(plans = before.financial.plans + plan))
        val sequence = nextSequence()
        entries += AuditEntry("confirm:$ordinal", sequence, "run", AuditType.COMMAND,
            request = EngineRequest("confirm:$ordinal", null, EngineCommand.ConfirmBudget(
                checkNotNull(before.economy.planning).id, 0)), before = before, after = game)
    }

    fun move(kind: LedgerKind, amount: Long) {
        val before = game
        val nextMoney = when (kind) {
            LedgerKind.INCOME -> EconomyOperations.earn(game.economy, amount)
            LedgerKind.AVAILABLE_EXPENSE -> EconomyOperations.spend(game.economy, amount, SpendingKind.GENERAL)
            LedgerKind.SAVINGS_EXPENSE -> EconomyOperations.spend(game.economy, amount, SpendingKind.GOAL)
            LedgerKind.DEPOSIT -> EconomyOperations.deposit(game.economy, amount)
            LedgerKind.WITHDRAWAL -> EconomyOperations.withdraw(game.economy, amount, confirmed = true)
        }
        game = game.copy(economy = nextMoney)
        val sequence = nextSequence()
        val command = when (kind) {
            LedgerKind.DEPOSIT -> EngineCommand.DepositSavings(amount)
            LedgerKind.WITHDRAWAL -> EngineCommand.WithdrawSavings(amount, true)
            LedgerKind.SAVINGS_EXPENSE -> EngineCommand.BuyGoalItem("goal", "item:$sequence")
            else -> EngineCommand.CompleteEvent("event:$sequence", "choice:$sequence")
        }
        entries += AuditEntry("movement:$sequence", sequence, "run", AuditType.COMMAND,
            request = EngineRequest("movement:$sequence", null, command), before = before, after = game,
            operations = listOf(LedgerEntry("operation:$sequence", kind, amount)))
    }

    fun technical(after: GameState) {
        val sequence = nextSequence()
        entries += AuditEntry("technical:$sequence", sequence, "run", AuditType.TECHNICAL_UPDATE, before = game, after = after)
        game = after
    }

    fun fact() {
        val sequence = nextSequence()
        entries += AuditEntry("fact:$sequence", sequence, "run", AuditType.FACTS)
    }

    private fun nextSequence() = (entries.lastOrNull()?.sequence ?: 0) + 1
}
