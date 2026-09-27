package ru.nksk.lctapp.domain.analytics

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nksk.lctapp.domain.analytics.FactDetail.*
import ru.nksk.lctapp.domain.analytics.SkillId.*
import ru.nksk.lctapp.domain.analytics.ObservationOutcome.*

class SkillEvaluatorTest {
    private val evaluator = SkillEvaluator()

    @Test fun canonicalFactsRoundTripWithTypedTasksAndStableDiscriminators() {
        val original = positiveScenarios()
        val json = Json { encodeDefaults = true; classDiscriminator = "_type" }
        val encoded = json.encodeToString(original)
        assertTrue(encoded.contains("budget_confirmed"))
        assertTrue(encoded.contains("read_ledger"))
        assertEquals(original, json.decodeFromString<List<AnalyticsFact>>(encoded))
    }

    @Test fun everySkillHasAnExecutablePositiveScenario() {
        val facts = positiveScenarios()
        val observations = evaluator.evaluate(facts)
        assertEquals(SkillId.entries.toSet(), observations.map { it.skill }.toSet())
        assertEquals(12, observations.size)
        observations.forEach { assertEquals("${it.skill}: ${it.reason}", SUPPORTED, it.outcome) }
        val profiles = evaluator.project("run", facts)
        assertEquals(12, profiles.size)
        profiles.forEach { assertEquals(it.skill.id, 1, it.supportedEpisodes) }
    }

    @Test fun replayOrderAndDuplicateDeliveryDoNotMultiplyObservations() {
        val facts = positiveScenarios()
        val expected = evaluator.evaluate(facts)
        assertEquals(expected, evaluator.evaluate(facts.reversed() + facts + facts))
        assertEquals(evaluator.project("run", facts), evaluator.project("run", facts.reversed() + facts))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sameFactIdWithDifferentPayloadIsAVisibleConflict() {
        val fact = fact("budget", 1, budget(35))
        evaluator.evaluate(listOf(fact, fact.copy(detail = budget(10))))
    }

    @Test fun originalShortfallIsNotAPlanningFailure() {
        val c = context(availableBefore = 10, availableAfter = 10, needs = 35)
        val result = single(fact("budget", 1, budget(10, allocationBase = 10), c))
        assertEquals(NEUTRAL, result.outcome)
        assertEquals(ObservationReason.ORIGINAL_SHORTFALL, result.reason)
        assertEquals(25L, result.measures["baselineShortfall"])
    }

    @Test fun omittedKnownNeedsAndImpossibleDistributionAreDifferentFromIncomeAdaptation() {
        val bad = single(fact("budget", 1, budget(10)))
        assertEquals(DIFFICULTY, bad.outcome)
        assertEquals(ObservationReason.KNOWN_NEED_OMITTED, bad.reason)
        val adaptation = single(fact("budget", 1, budget(35).copy(cause = BudgetRevisionCause.NEW_INCOME)))
        assertEquals(SUPPORTED, adaptation.outcome)
        val unknown = single(fact("budget", 1, budget(10).copy(cause = BudgetRevisionCause.UNSPECIFIED),
            context().copy(alternativeAvailable = null)))
        assertEquals(NEUTRAL, unknown.outcome)
        val exceedsFunds = single(fact("budget", 1, budget(101)))
        assertEquals(ObservationReason.INFEASIBLE_ALLOCATION, exceedsFunds.reason)
    }

    @Test fun purchaseIsNotBadMerelyBecauseItIsADesire() {
        val purchase = OptionalPurchase("cap", 25, true)
        assertEquals(SUPPORTED, single(fact("purchase", 1, purchase,
            context(availableBefore = 80, availableAfter = 55, needs = 25))).outcome)
        assertEquals(DIFFICULTY, single(fact("purchase", 1, purchase,
            context(availableBefore = 30, availableAfter = 5, needs = 25))).outcome)
        assertEquals(NEUTRAL, single(fact("purchase", 1, purchase.copy(purchased = false),
            context(availableBefore = 10, availableAfter = 10, needs = 25))).outcome)
    }

    @Test fun savingsAreNotAutomaticallyAddedToSpendingCoverage() {
        val c = context(availableBefore = 30, availableAfter = 5, needs = 25).copy(
            before = FinancialPosition(30, 80, 25), after = FinancialPosition(5, 80, 25))
        val purchase = fact("purchase", 1, OptionalPurchase("cap", 25, true), c)
        assertEquals(DIFFICULTY, single(purchase).outcome)
        val plan = FundingPlan("withdraw", 20, selected = true, feasibleBeforeNeed = true,
            guaranteedAmount = true, source = FundingSource.CONFIRMED_SAVINGS_WITHDRAWAL)
        assertEquals(SUPPORTED, single(purchase.copy(context = c.copy(fundingPlan = plan))).outcome)
        assertEquals(DIFFICULTY, single(purchase.copy(context = c.copy(fundingPlan = plan.copy(guaranteedAmount = false)))).outcome)
        assertEquals(DIFFICULTY, single(purchase.copy(context = c.copy(fundingPlan = plan.copy(amount = 81)))).outcome)
    }

    @Test fun intervalUsesManagedDecisionsAndCannotBlameUnavoidableExpense() {
        val base = IncomeWindow("week", true, true, listOf("purchase"), false)
        assertEquals(NEUTRAL, single(fact("interval", 1, base)).outcome)
        assertEquals(DIFFICULTY, single(fact("interval", 1, base.copy(
            avoidableUncoveredNeedIds = listOf("food"), availableRemedyWasShown = true))).outcome)
        assertEquals(INSUFFICIENT_DATA, single(fact("interval", 1, base.copy(historyComplete = false))).outcome)
        assertEquals(NEUTRAL, single(fact("interval", 1, base.copy(managedDecisionIds = emptyList(), knownNeedsMet = true))).outcome)
    }

    @Test fun circularTransfersAcrossDifferentWeeksDoNotFakeRegularSaving() {
        val facts = listOf(
            saving(1, "week1", SavingMovementKind.DEPOSIT, 10),
            saving(2, "week1", SavingMovementKind.WITHDRAWAL, 10),
            saving(3, "week2", SavingMovementKind.DEPOSIT, 10),
            fact("savings", 4, SavingCycleClosed("goal", true)),
        )
        val result = evaluator.evaluate(facts).single()
        assertEquals(INSUFFICIENT_DATA, result.outcome)
        assertEquals(1L, result.measures["incomeWindows"])
        assertEquals(10L, result.measures["netContribution"])
    }

    @Test fun repeatedUnfulfilledSavingsIntentionsRequireActualAvailableOpportunity() {
        val intentions = listOf(1, 2).map { index -> fact("savings", index.toLong(),
            SavingIntentionResolved("goal", "window$index", 10, 0, true, true)) }
        val closed = fact("savings", 3, SavingCycleClosed("goal", true))
        assertEquals(DIFFICULTY, evaluator.evaluate(intentions + closed).single().outcome)
        val impossible = intentions.map { it.copy(context = context(availableBefore = 10, availableAfter = 10, needs = 35)) }
        assertEquals(NEUTRAL, evaluator.evaluate(impossible + closed).single().outcome)
    }

    @Test fun refusalAloneDoesNotShowDeferredDesireAndAPauseIsNotFailure() {
        val noDesire = fact("desire", 1, DesireDeferred("cap", 25, false, "goal"))
        assertEquals(NEUTRAL, single(noDesire).outcome)
        val awaiting = noDesire.copy(detail = DesireDeferred("cap", 25, true, "goal"))
        assertEquals(EpisodeCompletion.PENDING, single(awaiting).completion)
        val changed = fact("desire", 2, PriorityConflict("goal", false, true, null))
        assertEquals(NEUTRAL, evaluator.evaluate(listOf(awaiting, changed)).single().outcome)
        val misunderstood = changed.copy(detail = PriorityConflict("goal", true, true,
            AssessmentTask.ExplainCause("spending", "income", listOf("purchase"))))
        assertEquals(DIFFICULTY, evaluator.evaluate(listOf(awaiting, misunderstood)).single().outcome)
    }

    @Test fun reserveCannotBeMoneyAlreadyNeededForFoodAndUsingItIsNormal() {
        val overstated = fact("reserve", 1, ReserveDecision("reserve", 10, 10, 0, true),
            context(availableBefore = 35, availableAfter = 35, needs = 35))
        assertEquals(DIFFICULTY, single(overstated).outcome)
        val used = fact("reserve", 1, ReserveDecision("reserve", 10, 6, 4, true,
            listOf(ReserveApplication("repair", 4))))
        assertEquals(SUPPORTED, single(used).outcome)
        assertEquals(ObservationReason.RESERVE_USED, single(used).reason)
    }

    @Test fun anUnaffordableFirstDesireDoesNotHideALaterVoluntaryDeferralForTheSameGoal() {
        val unavailable = fact("desire", 1, DesireDeferred("hat", 25, true, "goal"),
            context(availableBefore = 10, availableAfter = 10))
        val voluntary = fact("desire", 2, DesireDeferred("hat", 25, true, "goal"))
        val applied = fact("desire", 3, PriorityApplied("goal", "deposit", 15))
        assertEquals(SUPPORTED, evaluator.evaluate(listOf(unavailable, voluntary, applied)).single().outcome)
        assertEquals(NEUTRAL, evaluator.evaluate(listOf(unavailable, applied)).single().outcome)
    }

    @Test fun unexpectedExpenseNeedsARealResponseAndAvailableAlternativeForDifficulty() {
        val expense = fact("recovery", 1, UnexpectedExpense("repair", 10, false)).copy(actor = AnalyticsActor.SYSTEM)
        assertEquals(EpisodeCompletion.PENDING, single(expense).completion)
        val response = fact("recovery", 2, RecoveryAction("repair", "purchase", true),
            context(availableBefore = 25, availableAfter = 10, needs = 35))
        assertEquals(DIFFICULTY, evaluator.evaluate(listOf(expense, response)).single().outcome)
        assertEquals(NEUTRAL, evaluator.evaluate(listOf(expense, response.copy(context = response.context.copy(alternativeAvailable = false)))).single().outcome)
        val known = expense.copy(detail = UnexpectedExpense("repair", 10, true))
        assertEquals(NEUTRAL, evaluator.evaluate(listOf(known, response)).single().outcome)
    }

    @Test fun moneyOptionCanBeBetterWhenEffortIsNeededForTodaysWork() {
        val d = resourceChoice()
        assertEquals(SUPPORTED, single(fact("resource", 1, d)).outcome)
        assertEquals(DIFFICULTY, single(fact("resource", 1, d.copy(
            chosenCost = d.alternativeCost, alternativeCost = d.chosenCost))).outcome)
        assertEquals(NEUTRAL, single(fact("resource", 1, d.copy(priorityId = null))).outcome)
    }

    @Test fun enoughResourcesDoNotProveAnUnknownPriorityGuardAllowsTheDeed() {
        assertEquals(INSUFFICIENT_DATA, single(fact("resource", 1,
            resourceChoice().copy(chosenPriorityFeasible = null))).outcome)
        assertEquals(NEUTRAL, single(fact("resource", 1,
            resourceChoice().copy(chosenPriorityFeasible = false, alternativePriorityFeasible = false))).outcome)
    }

    @Test fun earningsUsesInclusiveDeadlineAndActualRewardWithoutMotorSkillPenalty() {
        val plan = fact("earning", 1, EarningPlan("offer", "food", 1, 2, 2, 0, 10))
        assertEquals(EpisodeCompletion.PENDING, single(plan).completion)
        val done = fact("earning", 2, EarningCompleted("offer", 2, 1, true))
        assertEquals(SUPPORTED, evaluator.evaluate(listOf(plan, done)).single().outcome)
        assertEquals(1L, evaluator.evaluate(listOf(plan, done)).single().measures["actualReward"])
        val today = plan.copy(detail = EarningPlan("offer", "food", 1, 1, 2, 0, 10))
        assertEquals(DIFFICULTY, single(today).outcome)
    }

    @Test fun correctedAnswerDoesNotEraseFirstMistakeOrTurnItIntoTwoObservations() {
        val first = fact("quiz", 1, QuestionAnswer("q", 1, AssessmentTask.CompareAmounts(8, 12, ComparisonSide.LEFT)))
        val corrected = fact("quiz", 2, QuestionAnswer("q", 2, AssessmentTask.CompareAmounts(8, 12, ComparisonSide.RIGHT)),
            context().copy(assistance = setOf(Assistance.HINT)))
        val result = evaluator.evaluate(listOf(first, corrected)).single()
        assertEquals(DIFFICULTY, result.outcome)
        assertEquals(1L, result.measures["questions"])
        assertEquals(0L, result.measures["correctFirstAnswers"])
    }

    @Test fun simulationFinanceIsExcludedButCounterfactualQuizIsRealLearning() {
        val ledger = fact("ledger", 1, ledgerQuestion()).copy(learningContext = LearningContext.COUNTERFACTUAL)
        assertEquals(SUPPORTED, single(ledger).outcome)
        assertEquals(setOf(LearningContext.COUNTERFACTUAL), single(ledger).learningContexts)
        assertTrue(evaluator.evaluate(listOf(ledger.copy(mode = AnalyticsMode.SIMULATION))).isEmpty())
        val purchase = fact("purchase", 2, OptionalPurchase("cap", 5, true)).copy(learningContext = LearningContext.COUNTERFACTUAL)
        assertTrue(evaluator.evaluate(listOf(purchase)).isEmpty())
        val cause = fact("cause", 3, causeQuestion()).copy(learningContext = LearningContext.COUNTERFACTUAL)
        assertEquals(EpisodeCompletion.PENDING, single(cause).completion)
        val apply = fact("cause", 4, ComparableApplication("needs", "new-purchase", true))
        assertEquals(SUPPORTED, evaluator.evaluate(listOf(cause, apply)).single().outcome)
    }

    @Test fun revealedAnswerIsNotIndependentAndLedgerNeverDoubleCountsTransfers() {
        val revealed = fact("ledger", 1, ledgerQuestion().copy(answerWasRevealed = true))
        val profile = evaluator.project("run", listOf(revealed)).single { it.skill == UNDERSTAND_INCOME_AND_EXPENSES }
        assertEquals(1, profile.supportedEpisodes)
        assertEquals(0, profile.supportedWithoutGameHints)
        val entries = listOf(LedgerEntry("income", LedgerKind.INCOME, 10),
            LedgerEntry("food", LedgerKind.AVAILABLE_EXPENSE, 5),
            LedgerEntry("deposit", LedgerKind.DEPOSIT, 3),
            LedgerEntry("part", LedgerKind.SAVINGS_EXPENSE, 2))
        val task = AssessmentTask.ReadLedger(0, 0, entries, LedgerQuestion.EXPENSE, 7)
        assertTrue(task.isCorrect())
        assertEquals(2L, task.copy(question = LedgerQuestion.AVAILABLE_REMAINDER).expectedAnswer())
        assertEquals(1L, task.copy(question = LedgerQuestion.SAVINGS_REMAINDER).expectedAnswer())
    }

    @Test fun missingShownContextAndUiViewsCannotAwardSkills() {
        assertEquals(INSUFFICIENT_DATA, single(fact("budget", 1, budget(35), DecisionContext())).outcome)
        val profiles = evaluator.project("run", listOf(fact("view", 1, Interaction("history_viewed"))))
        assertEquals(12, profiles.size)
        assertTrue(profiles.none { it.hasObservations })
        assertTrue(evaluator.project("another-run", positiveScenarios()).none { it.hasObservations })
        assertTrue(evaluator.evaluate(listOf(fact("practice", 1,
            PracticeAnswer("recovery", PracticeKind.SAVINGS_REHEARSAL, "next-income", "next-income", 1, true)))).isEmpty())
    }

    private fun positiveScenarios(): List<AnalyticsFact> = listOf(
        fact("compare", 1, QuestionAnswer("compare", 1, AssessmentTask.CompareAmounts(8, 12, ComparisonSide.RIGHT))),
        fact("budget", 2, budget(35)),
        fact("purchase", 3, OptionalPurchase("cap", 25, true)),
        fact("window", 4, IncomeWindow("week", true, true, listOf("purchase"), true)),
        saving(5, "week1", SavingMovementKind.DEPOSIT, 10),
        saving(6, "week2", SavingMovementKind.DEPOSIT, 15),
        saving(7, "week2", SavingMovementKind.GOAL_PURCHASE, 24),
        fact("savings", 8, SavingCycleClosed("goal", true)),
        fact("desire", 9, DesireDeferred("cap", 25, true, "goal")),
        fact("desire", 10, PriorityApplied("goal", "goal-purchase", 24)),
        fact("reserve", 11, ReserveDecision("reserve", 10, 6, 4, true, listOf(ReserveApplication("repair", 4)))),
        fact("repair", 12, UnexpectedExpense("repair", 4, false)).copy(actor = AnalyticsActor.SYSTEM),
        fact("repair", 13, RecoveryAction("repair", "deed", true)),
        fact("resource", 14, resourceChoice()),
        fact("earning", 15, EarningPlan("offer", "goal", 1, 2, 2, 3, 10)),
        fact("earning", 16, EarningCompleted("offer", 2, 8, true)),
        fact("explain", 17, causeQuestion()),
        fact("explain", 18, ComparableApplication("needs", "next-purchase", true)),
        fact("ledger", 19, ledgerQuestion()),
    )

    private fun budget(needs: Long, allocationBase: Long = 100) = BudgetConfirmed("plan", 1, allocationBase, needs, 0, 0, 0, BudgetRevisionCause.INITIAL)
    private fun resourceChoice() = ResourceChoice("pay", ResourceCost(4, 0, 0), ResourceCost(0, 2, 0), 3, 1, "work", 0, 3, 0,
        chosenPriorityFeasible = true, alternativePriorityFeasible = true)
    private fun causeQuestion() = QuestionAnswer("cause", 1, AssessmentTask.ExplainCause("spent", "spent", listOf("purchase")), comparisonFamily = "needs")
    private fun ledgerQuestion() = QuestionAnswer("ledger", 1, AssessmentTask.ReadLedger(0, 0, listOf(
        LedgerEntry("income", LedgerKind.INCOME, 10), LedgerEntry("food", LedgerKind.AVAILABLE_EXPENSE, 5),
        LedgerEntry("deposit", LedgerKind.DEPOSIT, 3)), LedgerQuestion.EXPENSE, 5))
    private fun saving(sequence: Long, window: String, kind: SavingMovementKind, amount: Long) =
        fact("savings", sequence, SavingMovement("operation$sequence", "goal", window, kind, amount))
    private fun fact(episode: String, sequence: Long, detail: FactDetail, context: DecisionContext = context()) =
        AnalyticsFact("$episode-$sequence", "run", episode, "action$sequence", sequence, detail, context)
    private fun context(availableBefore: Long = 100, availableAfter: Long = 75, needs: Long = 35) = DecisionContext(
        presentationId = "shown", informationPresented = true, complete = true,
        before = FinancialPosition(availableBefore, 0, needs), after = FinancialPosition(availableAfter, 0, needs),
        alternativeAvailable = true, financialPeriodId = "period", day = 1)
    private fun single(fact: AnalyticsFact) = evaluator.evaluate(listOf(fact)).single()
}
