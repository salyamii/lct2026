package ru.nksk.lctapp.domain.finance

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class FinancialBudgetProjectionTest {
    private val content = StoryContent(events = listOf(
        EventDefinition("want", EventType.WANT, "Шарф", "", null, null, null, 0, null, null),
        EventDefinition("repair", EventType.RANDOM, "Ремонт", "", null, null, null, 0, null, null)), choices = listOf(
        EventChoiceDefinition("buy", "want", 0, "Купить", -7, null, null, GoalImpact.NEUTRAL),
        EventChoiceDefinition("pay", "repair", 0, "Починить", -4, null, null, GoalImpact.NEUTRAL)))

    private class Fixture {
        private val period = FinancialPeriod("period", "goal", 1, 1, 100, 0)
        var state = GameState(PetState("PLAIN", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(0, 0, 0, 100)),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList(),
            EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, false, null, 100, emptyList(), emptyList()),
            selectedGoalId = "goal", financial = FinancialProgress(period.id, listOf(period)))
        val history = mutableListOf(AuditEntry("init", 1, "run", AuditType.INITIALIZED, after = state))
        fun plan(id: String, allocation: BudgetPlan, transferSavings: Boolean = false) {
            val revision = BudgetPlanRevision(id, "period", state.financial.plans.size + 1, 1,
                state.economy.availableBalance, allocation, BudgetRevisionReason.INITIAL)
            val allocated = state.economy.copy(plan = allocation, planning = null, unallocated = 0)
            val deposit = allocation.savings.takeIf { transferSavings } ?: 0L
            val economy = if (deposit == 0L) allocated else EconomyOperations.deposit(allocated, deposit)
            val next = state.copy(economy = economy,
                financial = state.financial.copy(plans = state.financial.plans + revision,
                    periods = state.financial.periods.map { it.copy(deposited = it.deposited + deposit) }))
            val receipt = if (deposit == 0L) emptyList() else
                listOf(LedgerEntry("action:${history.size}:deposit", LedgerKind.DEPOSIT, deposit))
            commit(next, EngineCommand.ConfirmBudget(id, 0), receipt)
        }
        fun operation(kind: LedgerKind, amount: Long, journalKind: DayJournalKind? = null, source: String = "") {
            val id = "operation:${history.size}"
            val old = state.financial.currentPeriod!!
            val nextPeriod = when (kind) {
                LedgerKind.INCOME -> old.copy(income = old.income + amount)
                LedgerKind.AVAILABLE_EXPENSE -> old.copy(spentAvailable = old.spentAvailable + amount)
                LedgerKind.SAVINGS_EXPENSE -> old.copy(spentSavings = old.spentSavings + amount)
                LedgerKind.DEPOSIT -> old.copy(deposited = old.deposited + amount)
                LedgerKind.WITHDRAWAL -> old.copy(withdrawn = old.withdrawn + amount)
            }
            val economy = state.economy
            val nextMoney = when (kind) {
                LedgerKind.INCOME -> EconomyOperations.earn(economy, amount)
                LedgerKind.AVAILABLE_EXPENSE -> EconomyOperations.spend(economy, amount, when {
                    journalKind == DayJournalKind.MEAL -> SpendingKind.FEEDING
                    source == "buy" -> SpendingKind.WANT
                    else -> SpendingKind.GENERAL
                })
                LedgerKind.SAVINGS_EXPENSE -> EconomyOperations.spend(economy, amount, SpendingKind.GOAL)
                LedgerKind.DEPOSIT -> EconomyOperations.deposit(economy, amount)
                LedgerKind.WITHDRAWAL -> EconomyOperations.withdraw(economy, amount, confirmed = true)
            }
            val engine = state.engine!!
            val journal = if (journalKind == null) emptyList() else listOf(DayJournalEntry(id, journalKind, source,
                if (kind == LedgerKind.INCOME) amount else -amount))
            commit(state.copy(economy = nextMoney, engine = engine.copy(journal = engine.journal + journal),
                financial = state.financial.copy(periods = listOf(nextPeriod))), EngineCommand.Feed("meal"),
                listOf(LedgerEntry(id, kind, amount)))
        }
        fun depositWeeklyReserve() {
            val income = 100L
            val period = checkNotNull(state.financial.currentPeriod)
            val funded = state.copy(economy = EconomyOperations.weekly(state.economy, "week:8", income),
                engine = state.engine!!.copy(day = 8),
                financial = state.financial.copy(periods = listOf(period.copy(income = period.income + income))))
            commit(funded, EngineCommand.BeginDay("week-eight", emptyList()),
                listOf(LedgerEntry("weekly-income", LedgerKind.INCOME, income)))
            val amount = state.economy.plan.reserve
            val request = EngineRequest("action:${history.size}", state.engine?.revision,
                EngineCommand.DepositWeeklyReserve("week:8", 0, amount))
            val after = FinancialPeriods.record(state, state.copy(economy =
                EconomyOperations.depositWeeklyReserve(state.economy, "week:8", 0, amount)), request)
            commit(after, request.command, CanonicalLedger.fromTransition(state, after, request))
        }
        private fun commit(next: GameState, command: EngineCommand, operations: List<LedgerEntry> = emptyList()) {
            val id = "action:${history.size}"
            history += AuditEntry(id, history.size + 1L, "run", AuditType.COMMAND,
                request = EngineRequest(id, state.engine?.revision, command), before = state, after = next, operations = operations)
            state = next
        }
    }

    @Test fun targetedPeriodReportMatchesTheWholeHistoryReportIncludingIncompleteEvidence() {
        val f = Fixture()
        f.plan("original", BudgetPlan(35, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 5, DayJournalKind.MEAL, "basic")
        f.plan("revised", BudgetPlan(30, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 7, DayJournalKind.EVENT_CHOICE, "buy")
        f.operation(LedgerKind.DEPOSIT, 20)
        val current = checkNotNull(f.state.financial.currentPeriod)
        val prior = current.copy(id = "previous", closedDay = 1)
        val state = f.state.copy(financial = f.state.financial.copy(periods = listOf(prior, current)))
        for (history in listOf(f.history.toList(), f.history.filterNot { it.sequence == 3L }, emptyList())) {
            val all = FinancialBudgetProjection.report(state, history, content)
            for (period in state.financial.periods) {
                assertEquals(all.first { it.periodId == period.id },
                    FinancialBudgetProjection.reportPeriod(state, period.id, history, content))
            }
        }
        assertNull(FinancialBudgetProjection.reportPeriod(state, "unknown", f.history, content))
        assertNull(FinancialBudgetProjection.reportPeriod(state, null, f.history, content))
    }

    @Test fun categoriesAndTransfersAreSeparatedAndGoalPurchaseIsNotAnotherDeposit() {
        val f = Fixture()
        f.plan("plan", BudgetPlan(35, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 5, DayJournalKind.MEAL, "basic")
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 7, DayJournalKind.EVENT_CHOICE, "buy")
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 4, DayJournalKind.EVENT_CHOICE, "pay")
        f.operation(LedgerKind.DEPOSIT, 20)
        f.operation(LedgerKind.SAVINGS_EXPENSE, 10, DayJournalKind.ITEM_PURCHASE, "part")
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        assertTrue(report.complete)
        assertEquals(BudgetActuals(needs = 5, wants = 7, reserve = 4, deposited = 20, goalPurchases = 10), report.actual)
        assertEquals(20L, report.actual.netSaved)
        assertEquals(16L, report.actual.availableExpenses)
        assertTrue(report.comparisons.single().complete)
    }

    @Test fun revisedRemainingPlanDoesNotInheritExpensesFromTheOriginalVersion() {
        val f = Fixture()
        f.plan("original", BudgetPlan(35, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 5, DayJournalKind.MEAL, "basic")
        f.plan("revised", BudgetPlan(30, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 7, DayJournalKind.EVENT_CHOICE, "buy")
        val comparisons = FinancialBudgetProjection.report(f.state, f.history, content).single().comparisons
        assertEquals(5L, comparisons.first().actual.needs)
        assertEquals(0L, comparisons.first().actual.wants)
        assertEquals("revised", comparisons.first().nextRevisionId)
        assertTrue(comparisons.first().finalised)
        assertEquals(0L, comparisons.last().actual.needs)
        assertEquals(7L, comparisons.last().actual.wants)
    }

    @Test fun weeklyReserveContributesToTheGoalWithoutFulfillingAnOlderOrFuturePlan() {
        val f = Fixture()
        f.plan("old-plan", BudgetPlan(35, 25, 25, 15))
        f.depositWeeklyReserve()
        val receiptReport = FinancialBudgetProjection.report(f.state, f.history, content).single()
        assertTrue(receiptReport.complete)
        assertEquals(15L, receiptReport.actual.deposited)
        assertEquals(0L, receiptReport.actual.availableExpenses)
        assertEquals(0L, receiptReport.comparisons.single().actual.deposited)
        assertFalse(receiptReport.comparisons.single().sourceActionIds.contains("action:3"))

        f.plan("new-plan", BudgetPlan(35, 125, 25, 0), transferSavings = true)
        val confirmedReport = FinancialBudgetProjection.report(f.state, f.history, content).single()
        assertTrue(confirmedReport.complete)
        assertEquals(40L, confirmedReport.actual.deposited)
        assertEquals(0L, confirmedReport.comparisons.first().actual.deposited)
        assertEquals(false, confirmedReport.comparisons.first().managedPlan)
        assertEquals(25L, confirmedReport.comparisons.last().actual.deposited)
    }

    @Test fun confirmationDepositBelongsToItsOwnRevisionAndNotThePreviousPlan() {
        val f = Fixture()
        f.plan("original", BudgetPlan(35, 20, 25, 20), transferSavings = true)
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 7, DayJournalKind.EVENT_CHOICE, "buy")
        f.plan("revised", BudgetPlan(35, 3, 10, 20), transferSavings = true)
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        assertTrue(report.complete)
        assertEquals(35L, report.actual.deposited)
        val original = report.comparisons.first()
        val revised = report.comparisons.last()
        assertEquals(25L, original.actual.deposited)
        assertEquals(7L, original.actual.wants)
        assertEquals(true, original.managedPlan)
        assertTrue(original.complete)
        assertEquals(listOf("action:1", "action:2"), original.sourceActionIds)
        assertEquals(10L, revised.actual.deposited)
        assertEquals(0L, revised.actual.wants)
        assertEquals(listOf("action:3"), revised.sourceActionIds)

        f.operation(LedgerKind.WITHDRAWAL, 8)
        f.plan("after-withdrawal", BudgetPlan(35, 3, 0, 28), transferSavings = true)
        val compared = FinancialBudgetProjection.report(f.state, f.history, content).single().comparisons
        assertEquals(25L, compared.first().actual.netSaved)
        assertEquals(2L, compared[1].actual.netSaved)
        assertEquals(false, compared[1].managedPlan)
        assertEquals(0L, compared.last().actual.deposited)
        assertTrue(compared.last().sourceActionIds.isEmpty())
    }

    @Test fun legacyPlanOnlyConfirmationStillHasNoSavingEvidence() {
        val f = Fixture()
        f.plan("legacy", BudgetPlan(35, 20, 25, 20))
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        assertEquals(0L, report.actual.deposited)
        assertEquals(0L, report.comparisons.single().actual.deposited)
        assertTrue(report.comparisons.single().sourceActionIds.isEmpty())
        assertEquals(100L, f.state.economy.availableBalance)
        assertEquals(0L, f.state.economy.savingsBalance)
    }

    @Test fun missingLegacyDetailsAndUnknownSourceAreNotZeroOrACompleteComparison() {
        val f = Fixture()
        f.plan("plan", BudgetPlan(35, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 9)
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        assertFalse(report.complete)
        assertFalse(report.comparisons.single().complete)
        assertEquals(9L, report.actual.unknownExpenses)
        val absent = FinancialBudgetProjection.report(f.state, emptyList(), content).single()
        assertEquals(9L, absent.actual.unknownExpenses)
        assertFalse(absent.complete)
    }

    @Test fun planReviewUsesActualCategoryAndGuidedRecoveryDoesNotInventHistory() {
        val f = Fixture()
        f.plan("plan", BudgetPlan(3, 25, 25, 47))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 5, DayJournalKind.MEAL, "basic")
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        val question = FinancialPeriods.question(f.state, "review", FinancialQuestionKind.PLAN_REVIEW, budgetReport = report)
        assertEquals("more", question.correctAnswerId)
        assertEquals("plan", question.reviewEvidence!!.planRevisionId)
        assertTrue(question.reviewEvidence!!.comparedKnownFacts)
        assertNull(question.ledgerTask)
        val guided = FinancialPeriods.question(f.state, "guided", FinancialQuestionKind.PLAN_REVIEW)
        assertTrue(guided.reviewEvidence!!.guidedRecovery)
        assertFalse(guided.reviewEvidence!!.comparedKnownFacts)
        assertNull(guided.reviewEvidence!!.planRevisionId)
    }

    @Test fun understoodOverspendingNeedsARealisticNewPlanAndNeverChangesMoney() {
        val f = Fixture()
        f.plan("plan", BudgetPlan(3, 25, 25, 47))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 5, DayJournalKind.MEAL, "basic")
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        val question = FinancialPeriods.question(f.state, "review", FinancialQuestionKind.PLAN_REVIEW, budgetReport = report)
        val evidence = question.reviewEvidence!!.copy(answerCorrect = true)
        assertFalse(FinancialProgressionPolicy.reviewReady(evidence))
        val oldPeriod = f.state.financial.currentPeriod!!
        val before = f.state.copy(financial = f.state.financial.copy(periods = listOf(oldPeriod.copy(reviewEvidence = evidence))),
            economy = f.state.economy.copy(planning = BudgetPlanning("revision", BudgetPlanningReason.MANUAL,
                BudgetPlanningStage.ALLOCATION, 0, draft = BudgetPlan(95, 0, 0, 0), baseAmount = 95)))
        val command = EngineCommand.ConfirmBudget("revision", 0)
        val after = before.copy(economy = before.economy.copy(plan = BudgetPlan(95, 0, 0, 0), planning = null))
        val unknownContext = FinancialPeriods.confirmed(before, after, EngineRequest("unknown", null, command), command, 30)
        assertFalse(FinancialProgressionPolicy.reviewReady(unknownContext.financial.currentPeriod!!.reviewEvidence))
        val context = DecisionContext("shown-plan", informationPresented = true, complete = true,
            before = FinancialPosition(95, 0, 30), alternativeAvailable = true)
        val fixed = FinancialPeriods.confirmed(before, after, EngineRequest("fixed", null, command, context), command, 30)
        assertTrue(FinancialProgressionPolicy.reviewReady(fixed.financial.currentPeriod!!.reviewEvidence))
        assertEquals(95L, fixed.economy.availableBalance)
        assertEquals(0L, fixed.economy.savingsBalance)
        assertEquals(before.financial.plans.first(), fixed.financial.plans.first())
    }

    @Test fun planReviewExplainsOnlyTheComparedAmountsWithoutChangingItsEvidence() {
        val f = Fixture()
        f.plan("plan", BudgetPlan(35, 25, 25, 15))
        f.operation(LedgerKind.AVAILABLE_EXPENSE, 15, DayJournalKind.MEAL, "basic")
        val before = f.state
        val report = FinancialBudgetProjection.report(f.state, f.history, content).single()
        val question = FinancialPeriods.question(before, "review", FinancialQuestionKind.PLAN_REVIEW, budgetReport = report)
        assertEquals("less", question.correctAnswerId)
        assertTrue(question.prompt.contains("выделили на еду и нужные покупки 35 монет"))
        assertTrue(question.prompt.contains("потратили 15"))
        assertTrue(question.explanation.contains("35 − 15 = 20"))
        assertEquals("plan", question.reviewEvidence!!.planRevisionId)
        assertEquals(listOf("period") + report.comparisons.single().sourceActionIds, question.sourceActionIds)
        assertEquals(before, f.state)
    }

    @Test fun savingsReviewComparesNetTransfersAndNeverCallsAWithdrawalAPurchase() {
        listOf(0L to "less", 10L to "less", 30L to "equal", 40L to "more").forEach { (deposit, answer) ->
            val f = Fixture()
            f.plan("plan", BudgetPlan(35, 25, 20, 20))
            val actual = BudgetActuals(deposited = deposit, withdrawn = 10)
            val revision = f.state.financial.plans.single()
            val report = PeriodBudgetReport("period", actual, complete = true, imported = false,
                comparisons = listOf(BudgetPlanComparison(revision, actual, complete = true, finalised = false,
                    sourceActionIds = listOf("deposit", "withdraw"))))
            val question = FinancialPeriods.question(f.state, "review", FinancialQuestionKind.PLAN_REVIEW, budgetReport = report)
            assertEquals(answer, question.correctAnswerId)
            assertTrue(question.prompt.contains("Положили: $deposit. Взяли обратно: 10."))
            assertFalse(question.prompt.contains("потратили", ignoreCase = true))
            assertFalse(question.explanation.contains("покуп", ignoreCase = true))
            if (deposit < 10) {
                assertTrue(question.explanation.contains("10 − $deposit = ${10 - deposit}"))
                assertTrue(question.explanation.contains("Накопления уменьшились"))
            } else assertTrue(question.explanation.contains("$deposit − 10 = ${deposit - 10}"))
            assertEquals(listOf("period", "deposit", "withdraw"), question.sourceActionIds)
            assertEquals("plan", question.reviewEvidence!!.planRevisionId)
        }
    }

    @Test fun anActualFeedingChoiceProvidesNeedsWithoutRequiringTheSeparateMealCommand() {
        val f = Fixture()
        val fed = f.state.copy(engine = f.state.engine!!.copy(ateToday = true))
        val updated = FinancialPeriods.record(f.state, fed,
            EngineRequest("fed-choice", 0, EngineCommand.CompleteEvent("event", "choice")))
        assertTrue(updated.financial.currentPeriod!!.needsProvided)
    }
}
