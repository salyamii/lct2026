package ru.nksk.lctapp.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class ConfirmBudgetLedgerTest {
    private val before = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(40, 20, 25, 15), savingsBalance = 10),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList())
    private val request = EngineRequest("confirmed", null, EngineCommand.ConfirmBudget("draft", 0))

    @Test fun confirmationRecordsTransferExactlyOnceAndNeverAsSpending() {
        val after = before.copy(economy = EconomyState(BudgetPlan(40, 20, 0, 15), savingsBalance = 35))
        val expected = listOf(LedgerEntry("confirmed:deposit", LedgerKind.DEPOSIT, 25))
        assertEquals(expected, CanonicalLedger.fromTransition(before, after, request))
        assertEquals(expected, CanonicalLedger.fromTransition(before, after, request))
        assertEquals(before.economy.balance, after.economy.balance)
    }

    @Test fun historicalPlanOnlyConfirmationDoesNotInventAReceipt() {
        val after = before.copy(economy = before.economy.copy(plan = BudgetPlan(40, 10, 35, 15)))
        assertTrue(CanonicalLedger.fromTransition(before, after, request).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedTwoAccountTransferCannotBeRecorded() {
        val after = before.copy(economy = EconomyState(BudgetPlan(40, 20, 0, 15), savingsBalance = 34))
        CanonicalLedger.fromTransition(before, after, request)
    }

    @Test(expected = IllegalArgumentException::class)
    fun confirmationCannotBeReinterpretedAsAWithdrawal() {
        val after = before.copy(economy = EconomyState(BudgetPlan(40, 20, 25, 20), savingsBalance = 5))
        CanonicalLedger.fromTransition(before, after, request)
    }

    @Test fun mixedOldAndNewConfirmationHistoryRoundTripsWithoutRewritingTheOldEntry() {
        val old = AuditEntry("old-confirm", 2, "run", AuditType.COMMAND,
            request = request.copy(id = "old-confirm"), before = before, after = before,
            contentFingerprint = "catalog-v1:transition-18:old")
        val oldPayload = HistoryCodec.encode(old)
        val after = before.copy(economy = EconomyState(BudgetPlan(40, 20, 0, 15), savingsBalance = 35))
        val fresh = AuditEntry("new-confirm", 3, "run", AuditType.COMMAND,
            request = request, before = before, after = after,
            operations = CanonicalLedger.fromTransition(before, after, request),
            contentFingerprint = "catalog-v1:transition-19:new")
        val snapshot = HistoryCodec.snapshot("run", after, listOf(
            AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = before), old, fresh))
        val restored = HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot))
        assertEquals(snapshot, restored)
        assertEquals(oldPayload, HistoryCodec.encode(restored.history[1]))
        assertTrue(restored.history[1].operations.isEmpty())
        assertEquals(LedgerKind.DEPOSIT, restored.history.last().operations.single().kind)
    }
}
