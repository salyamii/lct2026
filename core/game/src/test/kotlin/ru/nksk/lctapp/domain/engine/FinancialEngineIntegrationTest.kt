package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState

/** Public commands exercise real account movements separately from editable intentions. */
class FinancialEngineIntegrationTest {
    @Test fun everyTrainingTopicHasFourDurableQuestionsAndOnlyCorrectAnswersAdvance() = runTest {
        for (kind in FinancialQuestionKind.entries) {
            val f = Fixture()
            f.select(); f.confirm()
            val economy = f.state.economy
            val steps = f.state.engine!!.steps
            val pet = f.state.pet
            val initialMilestones = f.state.financial.currentPeriod!!.missingMilestones
            f.send(EngineCommand.RequestFinancialPractice(kind, series = true))
            val first = checkNotNull(f.state.financial.practice)
            val wrong = first.options.first { it.id != first.correctAnswerId }
            f.send(EngineCommand.AnswerFinancialQuestion(first.id, wrong.id), shown(first))
            val failed = checkNotNull(f.state.financial.practice)
            assertEquals(1, failed.attempts)
            assertFalse(failed.correct)
            assertEquals(initialMilestones, f.state.financial.currentPeriod!!.missingMilestones)
            assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.AdvanceFinancialPractice(first.id)))
            f.send(EngineCommand.CloseFinancialPractice)
            f.send(EngineCommand.RequestFinancialPractice(kind, series = true))
            assertEquals(failed, f.state.financial.practice)

            val prompts = mutableSetOf<String>()
            val ids = mutableSetOf<String>()
            repeat(4) { index ->
                val question = checkNotNull(f.state.financial.practice)
                prompts += question.prompt
                ids += question.id
                assertEquals(index + 1, question.series!!.questionNumber)
                assertEquals(4, question.series!!.totalQuestions)
                f.send(EngineCommand.AnswerFinancialQuestion(question.id, question.correctAnswerId), shown(question))
                assertTrue(f.state.financial.practice!!.correct)
                if (index == 0) assertEquals(2, f.state.financial.practice!!.attempts)
                if (index < 3) {
                    val answered = checkNotNull(f.state.financial.practice)
                    f.send(EngineCommand.RequestFinancialPractice(kind, series = true))
                    assertEquals(answered, f.state.financial.practice)
                    f.send(EngineCommand.AdvanceFinancialPractice(question.id))
                    assertEquals(BlockReason.InvalidEventAction, f.blocked(EngineCommand.AdvanceFinancialPractice(question.id)))
                }
            }
            assertEquals(4, prompts.size)
            assertEquals(4, ids.size)
            assertTrue(f.state.financial.practice!!.series!!.isLast)
            assertEquals(economy, f.state.economy)
            assertEquals(steps, f.state.engine!!.steps)
            assertEquals(pet, f.state.pet)
            assertFalse(f.state.financial.currentPeriod!!.independentlySaved)
            assertFalse(FinancialProgressionPolicy.summary(f.state.financial.currentPeriod!!.savingPractice!!).regular)
            assertTrue(f.repo.entries.flatMap { it.operations }.isEmpty())

            val answers = f.repo.entries.flatMap { it.facts }.filter {
                it.detail is FactDetail.QuestionAnswer || it.detail is FactDetail.PracticeAnswer
            }
            assertEquals(5, answers.size)
            assertTrue(answers.all { it.learningContext == LearningContext.TRAINING })
            assertTrue(answers.first().context.assistance.isEmpty())
            val observations = SkillEvaluator().evaluate(answers)
            assertTrue(observations.all { it.learningContexts == setOf(LearningContext.TRAINING) })
            assertTrue(observations.none { it.outcome == ObservationOutcome.SUPPORTED &&
                it.skill != SkillId.UNDERSTAND_INCOME_AND_EXPENSES })
            if (kind == FinancialQuestionKind.TRANSACTION_ACCOUNTING) {
                assertEquals(4, observations.size)
                assertTrue(observations.all { it.skill == SkillId.UNDERSTAND_INCOME_AND_EXPENSES })
                assertEquals(3, observations.count { it.outcome == ObservationOutcome.SUPPORTED })
                // Answering again after the explanation does not replace the original wrong answer.
                assertEquals(1, observations.count { it.outcome == ObservationOutcome.DIFFICULTY })
            }
            val retried = answers[1]
            assertTrue((retried.detail as? FactDetail.QuestionAnswer)?.answerWasRevealed == true ||
                Assistance.ANSWER_REVEALED in retried.context.assistance)

            val complete = checkNotNull(f.state.financial.practice)
            f.send(EngineCommand.CloseFinancialPractice)
            assertEquals(complete, f.state.financial.practice)
            f.send(EngineCommand.RequestFinancialPractice(kind, series = true))
            assertEquals(1, f.state.financial.practice!!.series!!.questionNumber)
            assertNotEquals(first.id, f.state.financial.practice!!.id)
        }
    }

    @Test fun futureTrainingQuestionsKeepTheirOriginalNumbersAcrossOtherGameplay() = runTest {
        val f = Fixture()
        f.select(); f.confirm()
        f.send(EngineCommand.RequestFinancialPractice(FinancialQuestionKind.TRANSACTION_ACCOUNTING, series = true))
        val first = checkNotNull(f.state.financial.practice)
        val next = first.series!!.remainingQuestions.first()
        f.send(EngineCommand.AnswerFinancialQuestion(first.id, first.correctAnswerId), shown(first))
        f.send(EngineCommand.DepositSavings(5))
        f.send(EngineCommand.AdvanceFinancialPractice(first.id))
        assertEquals(next, f.state.financial.practice!!.copy(series = null))
        assertEquals(95L, f.state.economy.availableBalance)
        assertEquals(5L, f.state.economy.savingsBalance)
    }

    @Test fun unfinishedLegacyQuestionKeepsItsIdentityWhenOpeningTraining() = runTest {
        val f = Fixture()
        f.select(); f.confirm()
        f.send(EngineCommand.RequestFinancialPractice())
        val legacy = checkNotNull(f.state.financial.practice)
        f.send(EngineCommand.RequestFinancialPractice(FinancialQuestionKind.PLAN_REVIEW, series = true))
        assertEquals(legacy, f.state.financial.practice)
        assertNull(f.state.financial.practice!!.series)
        f.send(EngineCommand.AnswerFinancialQuestion(legacy.id, legacy.correctAnswerId))
        f.send(EngineCommand.RequestFinancialPractice(FinancialQuestionKind.PLAN_REVIEW, series = true))
        assertNotNull(f.state.financial.practice!!.series)
    }

    private fun shown(question: FinancialQuestion) = DecisionContext(
        presentationId = "question:${question.id}", informationPresented = true, complete = true)

    @Test fun selectingGoalOpensItsPlanWithoutSpendingOrOpeningAnEvent() = runTest {
        val f = Fixture()
        val before = f.state
        f.select()
        assertEquals(before.economy.availableBalance, f.state.economy.availableBalance)
        assertEquals(before.economy.savingsBalance, f.state.economy.savingsBalance)
        assertEquals(BudgetPlanningReason.MANUAL, f.state.economy.planning!!.reason)
        assertEquals(BudgetPlanningStage.ALLOCATION, f.state.economy.planning!!.stage)
        assertEquals(0, f.state.engine!!.steps)
        assertNull(f.state.engine!!.currentEvent)
        assertEquals("goal", f.state.financial.currentPeriod!!.goalId)
        assertFalse(f.state.financial.currentPeriod!!.imported)
        assertTrue(f.state.financial.plans.isEmpty())
        assertEquals(BlockReason.BudgetPlanningRequired, f.blocked(EngineCommand.OpenNextEvent))
        assertTrue(f.repo.entries.single().operations.isEmpty())
    }

    @Test fun draftAndConfirmationPreserveBothAccountsAndKeepOriginalPlanVersion() = runTest {
        val f = Fixture()
        f.select()
        val original = f.state.economy.plan
        f.allocate(BudgetSection.NEEDS, 40)
        f.allocate(BudgetSection.SAVINGS, 60)
        assertEquals(original, f.state.economy.plan)
        assertEquals(100L, f.state.economy.availableBalance)
        assertEquals(0L, f.state.economy.savingsBalance)
        f.confirm()
        val first = f.state.financial.plans.single()
        assertEquals(BudgetPlan(40, 0, 60, 0), first.allocation)
        assertEquals(100L, first.availableBasis)
        f.send(EngineCommand.ChangeBudgetAllocation("revision", 0, BudgetSection.SAVINGS,
            amount = 50, startManual = true))
        f.allocate(BudgetSection.RESERVE, 10)
        f.confirm()
        assertEquals(first, f.state.financial.plans.first())
        assertEquals(first.id, f.state.financial.plans.last().previousId)
        assertEquals(2, f.state.financial.plans.last().ordinal)
        assertEquals(BudgetPlan(40, 0, 50, 10), f.state.economy.plan)
        assertEquals(100L, f.state.economy.availableBalance)
        assertEquals(0L, f.state.economy.savingsBalance)
        assertFalse(f.state.financial.currentPeriod!!.independentlySaved)
        assertTrue(f.repo.entries.flatMap { it.operations }.isEmpty())
    }

    @Test fun fullySavedGoalStillDebitsOnlySavingsAndKeepsItsHistoricalReceipt() = runTest {
        val f = Fixture()
        f.select(); f.confirm()
        val purchase = EngineCommand.BuyGoalItem("goal", "part")
        f.send(EngineCommand.DepositSavings(20))
        assertEquals(100L, f.state.economy.balance)
        assertEquals(80L, f.state.economy.availableBalance)
        assertEquals(20L, f.state.economy.savingsBalance)
        f.send(purchase)
        assertEquals(80L, f.state.economy.availableBalance)
        assertEquals(0L, f.state.economy.savingsBalance)
        assertEquals(1, f.state.engine!!.steps)
        assertEquals(listOf("part"), f.state.ownedItems.map { it.itemId })
        assertEquals(listOf(LedgerKind.DEPOSIT, LedgerKind.SAVINGS_EXPENSE),
            f.repo.entries.flatMap { it.operations }.map { it.kind })
        val period = f.state.financial.currentPeriod!!
        assertEquals(20L, period.deposited)
        assertEquals(20L, period.spentSavings)
        assertEquals(0L, period.spentAvailable)
        val receipt = f.repo.entries.last()
        val receiptBefore = checkNotNull(receipt.before)
        val receiptAfter = checkNotNull(receipt.after)
        val receiptRequest = checkNotNull(receipt.request)
        assertEquals(receiptAfter, f.engine.transition(receiptBefore, receiptRequest))
        assertEquals(receipt.operations, CanonicalLedger.fromTransition(receiptBefore, receiptAfter, receiptRequest))
    }

    @Test fun directGoalPurchaseRecordsWalletExpenseWithoutPretendingTheChildSaved() = runTest {
        val f = Fixture()
        f.select(); f.confirm()
        val before = f.state
        f.send(EngineCommand.BuyGoalItem("goal", "part"))

        assertEquals(80L, f.state.economy.availableBalance)
        assertEquals(0L, f.state.economy.savingsBalance)
        assertEquals(BudgetPlan(80, 0, 0, 0), f.state.economy.plan)
        assertEquals(before.financial.plans, f.state.financial.plans)
        val period = checkNotNull(f.state.financial.currentPeriod)
        assertEquals(20L, period.spentAvailable)
        assertEquals(0L, period.spentSavings)
        assertEquals(0L, period.deposited)
        assertEquals(0L, period.withdrawn)
        assertEquals(before.financial.currentPeriod!!.savingPractice, period.savingPractice)
        assertFalse(period.independentlySaved)
        val entry = f.repo.entries.last()
        assertEquals(listOf(LedgerKind.AVAILABLE_EXPENSE), entry.operations.map { it.kind })
        assertEquals(20L, entry.operations.single().amount)
        assertTrue(entry.facts.none { it.detail is FactDetail.SavingMovement || it.detail is FactDetail.PriorityApplied })
        val report = FinancialBudgetProjection.report(f.state, f.repo.entries, f.content).single()
        assertTrue(report.complete)
        assertEquals(20L, report.actual.goalPurchasesAvailable)
        assertEquals(20L, report.actual.availableExpenses)
        assertEquals(0L, report.actual.unknownExpenses)
        assertEquals(0L, report.actual.goalPurchases)
    }

    @Test fun mixedGoalPurchaseHasTwoReceiptsOneItemAndNoRepeatedEffectsAfterRetryOrSnapshot() = runTest {
        val f = Fixture(savings = 7)
        f.select(); f.confirm()
        val before = f.state
        val request = f.request(EngineCommand.BuyGoalItem("goal", "part"))
        assertNull(f.engine.blockReason(before, request.command))
        assertEquals(before, f.state)
        assertTrue(f.engine.dispatch(request) is EngineResult.Applied)

        val after = f.state
        assertEquals(87L, after.economy.availableBalance)
        assertEquals(0L, after.economy.savingsBalance)
        assertEquals(listOf("part"), after.ownedItems.map { it.itemId })
        assertEquals(before.engine!!.steps + 1, after.engine!!.steps)
        assertEquals(before.engine.energy, after.engine.energy)
        assertEquals(before.story, after.story)
        val period = checkNotNull(after.financial.currentPeriod)
        assertEquals(13L, period.spentAvailable)
        assertEquals(7L, period.spentSavings)
        assertEquals(0L, period.deposited)
        assertEquals(0L, period.withdrawn)
        val entry = f.repo.entries.last()
        assertEquals(listOf(LedgerKind.SAVINGS_EXPENSE, LedgerKind.AVAILABLE_EXPENSE), entry.operations.map { it.kind })
        assertEquals(listOf(7L, 13L), entry.operations.map { it.amount })
        assertEquals(2, entry.operations.map { it.operationId }.distinct().size)
        CanonicalLedger.validate(before, after, entry.operations)
        val savingsFact = entry.facts.map { it.detail }.filterIsInstance<FactDetail.SavingMovement>().single()
        assertEquals(SavingMovementKind.GOAL_PURCHASE, savingsFact.kind)
        assertEquals(7L, savingsFact.amount)
        assertEquals(entry.operations.first().operationId, savingsFact.operationId)

        val report = FinancialBudgetProjection.report(after, f.repo.entries, f.content).single()
        assertTrue(report.complete)
        assertEquals(BudgetActuals(goalPurchases = 7, goalPurchasesAvailable = 13), report.actual)
        val snapshot = HistoryCodec.snapshot("run", after, f.repo.entries)
        assertEquals(snapshot, HistoryCodec.decodeSnapshot(HistoryCodec.encodeSnapshot(snapshot)))
        assertEquals(after, f.engine.transition(before, request))
        assertEquals(entry.operations, CanonicalLedger.fromTransition(before, after, request))
        val historySize = f.repo.entries.size
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(request))
        assertEquals(BlockReason.ItemAlreadyOwned, f.blocked(request.command))
        assertEquals(after, f.state)
        assertEquals(historySize, f.repo.entries.size)
    }

    @Test fun mixedPurchaseFoodWarningWritesNothingAndConfirmationKeepsItsOriginalRevision() = runTest {
        val f = Fixture(available = 40, savings = 7)
        f.select(); f.confirm()
        val before = f.state
        val request = f.request(EngineCommand.BuyGoalItem("goal", "part"))
        val historySize = f.repo.entries.size
        assertEquals(BlockReason.FoodBudgetWarning(27, 35), f.engine.blockReason(before, request.command))
        assertEquals(EngineResult.Blocked(BlockReason.FoodBudgetWarning(27, 35)), f.engine.dispatch(request))
        assertEquals(before, f.state)
        assertEquals(historySize, f.repo.entries.size)

        val confirmed = request.copy(id = "confirmed-goal", command = EngineCommand.BuyGoalItem("goal", "part", acceptFoodRisk = true))
        f.send(EngineCommand.Feed("meal"))
        val latest = f.state
        assertEquals(EngineResult.Blocked(BlockReason.StaleRevision), f.engine.dispatch(confirmed))
        assertEquals(latest, f.state)
        assertTrue(f.state.ownedItems.isEmpty())
        f.send(EngineCommand.BuyGoalItem("goal", "part", acceptFoodRisk = true))
        assertEquals(22L, f.state.economy.availableBalance)
        assertEquals(0L, f.state.economy.savingsBalance)
        assertEquals(listOf("part"), f.state.ownedItems.map { it.itemId })
    }

    @Test fun goalPurchaseChecksCombinedFundsAndCannotBypassUnfinishedAllocation() = runTest {
        val f = Fixture(available = 10, savings = 5)
        f.select()
        val request = EngineCommand.BuyGoalItem("goal", "part", acceptFoodRisk = true)
        val draft = f.state
        assertEquals(BlockReason.BudgetPlanningRequired, f.blocked(request))
        assertEquals(draft, f.state)
        f.confirm()
        val before = f.state
        assertEquals(BlockReason.InsufficientMoney(5), f.blocked(request))
        assertEquals(before, f.state)
    }

    @Test fun liveAllocationsFollowTransfersAndFoodWhileConfirmedIntentRemainsImmutable() = runTest {
        val f = Fixture()
        f.select()
        f.allocate(BudgetSection.NEEDS, 40)
        f.allocate(BudgetSection.SAVINGS, 60)
        f.confirm()
        val intent = f.state.financial.plans.single()
        f.send(EngineCommand.DepositSavings(20))
        assertEquals(BudgetPlan(40, 0, 40, 0), f.state.economy.plan)
        f.send(EngineCommand.Feed("meal"))
        assertEquals(BudgetPlan(35, 0, 40, 0), f.state.economy.plan)
        f.send(EngineCommand.WithdrawSavings(10, confirmed = true))
        assertEquals(BudgetPlan(35, 0, 40, 10), f.state.economy.plan)
        assertEquals(85L, f.state.economy.availableBalance)
        assertEquals(10L, f.state.economy.savingsBalance)
        assertEquals(intent, f.state.financial.plans.single())

        // Today's food has been paid, so this new revision covers six remaining meals (30).
        f.send(EngineCommand.ChangeBudgetAllocation("remaining", 0, BudgetSection.NEEDS,
            amount = 30, startManual = true))
        f.allocate(BudgetSection.RESERVE, 15)
        f.confirm()
        assertEquals(BudgetPlan(30, 0, 40, 15), f.state.economy.plan)
        assertEquals(intent, f.state.financial.plans.first())
        assertEquals(BudgetPlan(40, 0, 60, 0), intent.allocation)
        assertEquals(85L, f.state.economy.availableBalance)
        assertEquals(10L, f.state.economy.savingsBalance)
        assertEquals(listOf(LedgerKind.DEPOSIT, LedgerKind.AVAILABLE_EXPENSE, LedgerKind.WITHDRAWAL),
            f.repo.entries.flatMap { it.operations }.map { it.kind })
    }

    @Test fun manualFoodMinimumIsEnforcedAtTheCommandBoundaryWithoutOpeningAnInvalidDraft() = runTest {
        val f = Fixture()
        f.select()
        f.allocate(BudgetSection.NEEDS, 35)
        f.allocate(BudgetSection.WANTS, 65)
        f.confirm()
        val before = f.state
        val historySize = f.repo.entries.size
        for (command in listOf(
            EngineCommand.ChangeBudgetAllocation("manual", 0, BudgetSection.NEEDS,
                amount = 30, startManual = true),
            EngineCommand.ChangeBudgetAllocation("manual", 0, BudgetSection.NEEDS,
                increase = false, startManual = true),
        )) {
            assertEquals(BlockReason.InvalidContent(EconomyFailure.INVALID_ALLOCATION.name), f.blocked(command))
            assertEquals(before, f.state)
            assertEquals(historySize, f.repo.entries.size)
        }
    }

    @Test fun savingsCannotPayFoodUntilWithdrawalIsExplicitlyConfirmed() = runTest {
        val f = Fixture(available = 0, savings = 40)
        f.select(); f.confirm()
        val before = f.state
        assertEquals(BlockReason.InsufficientMoney(5), f.blocked(EngineCommand.Feed("meal")))
        assertEquals(BlockReason.SavingsWithdrawalConfirmationRequired,
            f.blocked(EngineCommand.WithdrawSavings(10, confirmed = false)))
        assertEquals(before, f.state)
        f.send(EngineCommand.WithdrawSavings(10, confirmed = true))
        assertEquals(40L, f.state.economy.balance)
        assertEquals(10L, f.state.economy.availableBalance)
        assertEquals(30L, f.state.economy.savingsBalance)
        f.send(EngineCommand.Feed("meal"))
        assertEquals(5L, f.state.economy.availableBalance)
        assertEquals(30L, f.state.economy.savingsBalance)
        assertTrue(f.state.engine!!.ateToday)
        assertEquals(listOf(LedgerKind.WITHDRAWAL, LedgerKind.AVAILABLE_EXPENSE),
            f.repo.entries.flatMap { it.operations }.map { it.kind })
    }

    @Test fun finaleRequiresThisPeriodsPracticeAndNewPeriodDoesNotInheritOldReview() = runTest {
        val f = Fixture()
        f.select(); f.confirm()
        f.send(EngineCommand.DepositSavings(20))
        f.send(EngineCommand.BuyGoalItem("goal", "part"))
        f.send(EngineCommand.OpenNextEvent)
        f.completeCurrent("intro")
        val before = f.state
        assertEquals(BlockReason.FinancialPracticeRequired(listOf(
            FinancialMilestone.PROVIDE_NEEDS, FinancialMilestone.SAVE_FOR_GOAL, FinancialMilestone.REVIEW_PLAN)),
            f.blocked(EngineCommand.OpenNextEvent))
        assertEquals(before, f.state)
        f.send(EngineCommand.Feed("meal"))
        f.send(EngineCommand.RequestFinancialPractice())
        val question = f.state.financial.practice!!
        val wrong = question.options.first { it.id != question.correctAnswerId }
        f.send(EngineCommand.AnswerFinancialQuestion(question.id, wrong.id))
        assertFalse(f.state.financial.currentPeriod!!.reviewedPlan)
        assertEquals(BlockReason.FinancialPracticeRequired(listOf(FinancialMilestone.SAVE_FOR_GOAL, FinancialMilestone.REVIEW_PLAN)),
            f.blocked(EngineCommand.OpenNextEvent))
        f.send(EngineCommand.AnswerFinancialQuestion(question.id, question.correctAnswerId))
        assertEquals(listOf(FinancialMilestone.SAVE_FOR_GOAL), f.state.financial.currentPeriod!!.missingMilestones)
        val moneyBeforePractice = f.state.economy
        val depositsBeforePractice = f.state.financial.currentPeriod!!.deposited
        f.send(EngineCommand.RequestFinancialPractice(FinancialQuestionKind.SAVING_PRACTICE))
        val savingQuestion = checkNotNull(f.state.financial.practice)
        f.send(EngineCommand.AnswerFinancialQuestion(savingQuestion.id, savingQuestion.correctAnswerId))
        assertEquals(moneyBeforePractice, f.state.economy)
        assertEquals(depositsBeforePractice, f.state.financial.currentPeriod!!.deposited)
        assertFalse(FinancialProgressionPolicy.summary(f.state.financial.currentPeriod!!.savingPractice!!).regular)
        assertTrue(f.state.financial.currentPeriod!!.missingMilestones.isEmpty())
        f.send(EngineCommand.OpenNextEvent)
        f.completeCurrent("final")
        val closed = f.state.financial.periods.single()
        assertEquals(1, closed.closedDay)
        assertNull(f.state.financial.currentPeriodId)
        assertNull(f.state.selectedGoalId)
        f.send(EngineCommand.SelectGoal("next-goal"))
        val next = f.state.financial.currentPeriod!!
        assertEquals(2, next.ordinal)
        assertEquals(closed, f.state.financial.periods.first())
        assertEquals(FinancialMilestone.entries, next.missingMilestones)
        assertNull(f.state.financial.practice)
        f.confirmAllocatingRemaining()
        assertEquals(BlockReason.InvalidEventAction,
            f.blocked(EngineCommand.AnswerFinancialQuestion(question.id, question.correctAnswerId)))
        assertFalse(f.state.financial.currentPeriod!!.reviewedPlan)
    }

    private class Fixture(available: Long = 100, savings: Long = 0) {
        val repo = AuditRepository(GameState(PetState("PLAIN", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(available, 0, 0, 0), availableBalance = available, savingsBalance = savings),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList()))
        private val events = listOf("quiet", "intro", "final", "next-intro", "next-final").map { id ->
            EventDefinition(id, if (id == "quiet") EventType.RANDOM else EventType.STORY,
                id, id, null, null, null, 0, null, null)
        }
        val content = StoryContent(
            chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1)), events = events,
            choices = events.map { EventChoiceDefinition("${it.id}:done", it.id, 0, "Done", 0, null, null, GoalImpact.NEUTRAL) },
            goals = listOf(GoalDefinition("goal", "Goal", ""), GoalDefinition("next-goal", "Next", "")),
            items = listOf(ItemDefinition("part", "Part", "", priceCoins = 20), ItemDefinition("next-part", "Part", "", priceCoins = 20)),
            requiredItems = listOf(GoalRequiredItem("goal", "part"), GoalRequiredItem("next-goal", "next-part")),
        )
        private val policies = events.associate { it.id to EventPolicy(energyCost = 0,
            storyActId = when (it.id) { "intro", "final" -> "act"; "next-intro", "next-final" -> "next-act"; else -> null },
            finishesStoryAct = it.id in listOf("final", "next-final"),
            condition = when (it.id) { "final" -> StoryCondition.EventCompleted("intro");
                "next-final" -> StoryCondition.EventCompleted("next-intro"); else -> StoryCondition.Always }) }
        val engine = GameEngine(repo, EventFactory(content, policies, listOf(MealDefinition("meal", 5, null)),
            listOf(GoalCampaign("goal", "intro", listOf("part")), GoalCampaign("next-goal", "next-intro", listOf("next-part"))),
            StoryCampaign(listOf(StoryAct("act", "Act", "day", listOf("intro", "final"), "final"),
                StoryAct("next-act", "Next", "day", listOf("next-intro", "next-final"), "next-final")))),
            EngineRules("finance-test", 5, 3, 1))
        val state get() = repo.value
        private var id = 0
        fun request(command: EngineCommand, context: DecisionContext? = null) =
            EngineRequest("finance:${++id}", state.engine?.revision, command, context)
        suspend fun send(command: EngineCommand, context: DecisionContext? = null) {
            val result = engine.dispatch(request(command, context))
            assertTrue("$command -> $result", result is EngineResult.Applied)
        }
        suspend fun blocked(command: EngineCommand) = (engine.dispatch(request(command)) as EngineResult.Blocked).reason
        suspend fun select() = send(EngineCommand.SelectGoal("goal", EngineCommand.BeginDay("day", List(4) { "quiet" })))
        suspend fun allocate(section: BudgetSection, amount: Long) {
            val plan = state.economy.planning!!
            send(EngineCommand.ChangeBudgetAllocation(plan.id, plan.revision, section, amount = amount))
        }
        suspend fun confirm() {
            val plan = state.economy.planning!!
            send(EngineCommand.ConfirmBudget(plan.id, plan.revision))
        }
        suspend fun confirmAllocatingRemaining() {
            allocate(BudgetSection.NEEDS, state.economy.displayPlan.needs + EconomyOperations.allocationRemaining(state.economy))
            confirm()
        }
        suspend fun completeCurrent(expected: String) {
            val event = state.engine!!.currentEvent!!
            assertEquals(expected, event.eventId)
            send(EngineCommand.CompleteEvent(event.id, "$expected:done"))
        }
    }

    /** In-memory atomic boundary captures the very facts and receipts production storage receives. */
    private class AuditRepository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        val value get() = flow.value
        val entries = mutableListOf<AuditEntry>()
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun readHistory() = entries.toList()
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { flow.value = it }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            val before = value
            val after = transform(before)
            val sequence = entries.size.toLong() + 1
            val entry = AuditEntry(request.id, sequence, "run", AuditType.COMMAND, request, context, before, after,
                facts(before, after, "run", sequence), CanonicalLedger.fromTransition(before, after, request),
                contentFingerprint = contentFingerprint)
            entries += entry
            flow.value = after
            return after
        }
    }
}
