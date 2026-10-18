package ru.nksk.lctapp.domain.engine

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class WeeklyReserveEvidenceTest {
    private val rules = EngineRules("test-rules", 5, 3, 1)
    private val analytics = EngineAnalytics(
        EventFactory(StoryContent(), emptyMap(), listOf(MealDefinition("basic", 5, null))),
        rules, "catalog-v1:transition-20:test",
        preview = { _, _ -> error("Reserve transfer must not preview actions") },
        previewEventChoice = { _, _, _ -> error("Reserve transfer must not preview choices") },
    )
    private val period = FinancialPeriod("period", "goal", 1, 1, 55, 10, income = 100,
        savingPractice = FinancialProgressionPolicy.opening("period", 10)
            .copy(incomeOpportunityId = "income:begin-week"))
    private val before = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyOperations.weekly(EconomyState(BudgetPlan(35, 5, 0, 15), savingsBalance = 10), "week:8", 100),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList(),
        engine = EngineState(rules.id, 8, 8, DayPhase.RUNNING, 0, 5, false, null, 65, emptyList(), emptyList()),
        selectedGoalId = "goal", financial = FinancialProgress(period.id, listOf(period)))
    private val request = EngineRequest("reserve", 8, EngineCommand.DepositWeeklyReserve("week:8", 0, 15),
        DecisionContext("weekly:shown", informationPresented = true, complete = true,
            before = FinancialPosition(155, 10, 35), alternativeAvailable = true))

    private fun transfer(request: EngineRequest = this.request): GameState {
        val after = before.copy(economy = EconomyOperations.depositWeeklyReserve(before.economy, "week:8", 0, 15))
        return FinancialPeriods.record(before, after, request)
    }

    @Test fun oneTransferAddsSavingEvidenceWithoutIncomeSpendingOrAnotherPlan() {
        val after = transfer()
        val receipt = CanonicalLedger.fromTransition(before, after, request).single()
        assertEquals(LedgerEntry("reserve:deposit", LedgerKind.DEPOSIT, 15), receipt)
        assertEquals(before.economy.balance, after.economy.balance)
        val savedPeriod = checkNotNull(after.financial.currentPeriod)
        assertEquals(100L, savedPeriod.income)
        assertEquals(0L, savedPeriod.spentAvailable)
        assertEquals(0L, savedPeriod.spentSavings)
        assertEquals(15L, savedPeriod.deposited)
        assertEquals(before.financial.plans, after.financial.plans)
        assertEquals(before.engine, after.engine)
        val contribution = checkNotNull(savedPeriod.savingPractice).contributions.single()
        assertEquals(receipt.operationId, contribution.operationId)
        assertEquals(15L, contribution.retained)
        assertEquals(8, contribution.day)
        assertEquals("income:begin-week", contribution.incomeOpportunityId)
        assertTrue(contribution.independent)
        assertTrue(contribution.needsCovered)
    }

    @Test fun analyticsKeepsTheCurrentPeriodAndDoesNotInventPriorityOrReserveIntentions() {
        val after = transfer()
        val facts = analytics.facts(request, before, after, "run", 2)
        val fact = facts.single { it.detail is FactDetail.SavingMovement }
        val movement = fact.detail as FactDetail.SavingMovement
        assertEquals("reserve:deposit", movement.operationId)
        assertEquals("goal", movement.goalId)
        assertEquals("income:begin-week", movement.incomeWindowId)
        assertEquals(SavingMovementKind.DEPOSIT, movement.kind)
        assertEquals(15L, movement.amount)
        assertEquals(FinancialPosition(155, 10, 35), fact.context.before)
        assertEquals(FinancialPosition(140, 25, 35), fact.context.after)
        assertEquals("period", fact.context.financialPeriodId)
        assertEquals(8, fact.context.day)
        assertFalse(facts.any { it.detail is FactDetail.PriorityApplied ||
            it.detail is FactDetail.ReserveDecision || it.detail is FactDetail.BudgetConfirmed })
    }

    @Test fun missingOrAssistedPresentationDoesNotBecomeIndependentSaving() {
        for (shown in listOf(null, request.context!!.copy(assistance = setOf(Assistance.HINT)))) {
            val attempt = request.copy(context = shown)
            val after = transfer(attempt)
            val practice = checkNotNull(after.financial.currentPeriod!!.savingPractice)
            assertFalse(practice.contributions.single().independent)
            assertEquals(0, FinancialProgressionPolicy.summary(practice).creditedDecisions)
            val context = analytics.facts(attempt, before, after, "run", 2)
                .single { it.detail is FactDetail.SavingMovement }.context
            assertEquals(shown?.complete ?: false, context.complete)
            assertEquals(shown?.informationPresented ?: false, context.informationPresented)
            assertEquals(shown?.assistance.orEmpty(), context.assistance)
        }
    }

    @Test fun weeklyCommandAndItsReceiptSurviveSnapshotRoundTrip() {
        val after = transfer()
        val command = AuditEntry("reserve", 2, "run", AuditType.COMMAND, request,
            before = before, after = after, operations = CanonicalLedger.fromTransition(before, after, request),
            contentFingerprint = "catalog-v1:transition-20:test")
        val snapshot = HistoryCodec.snapshot("run", after, listOf(
            AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = before), command))
        assertEquals(snapshot, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)))
    }
}
