package ru.nksk.lctapp.domain.engine

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.minigame.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.StoryState

/** Starts with the ordinary 100-coin receipt and no engine/history; never injects a completed skill. */
class FinancialSkillJourneyTest {
    @Test fun aNewRunOffersEvidenceForAllTwelveSkillsWithoutRestoringALegacyFixture() = runTest {
        val f = Journey()
        f.planInitial()
        f.send(EngineCommand.BeginDay("day", listOf("intro", "work", "travel", "want", "repair")))
        f.send(EngineCommand.Feed("meal"))
        f.openAndChoose("intro", "intro:done")
        f.send(EngineCommand.OpenNextEvent)
        val proposal = f.state.engine!!.currentEvent!!
        assertEquals("work", proposal.eventId)
        f.send(EngineCommand.DismissDeedProposal(proposal.id))
        val offer = f.state.engine!!.deeds.single()
        f.send(EngineCommand.OpenNextEvent)
        assertEquals("travel", f.state.engine!!.currentEvent!!.eventId)
        f.send(EngineCommand.CompleteEvent(f.state.engine!!.currentEvent!!.id, "travel:walk", resourcePriorityOfferId = offer.id))
        f.openAndChoose("want", "want:later", priority = "goal")
        f.send(EngineCommand.DepositSavings(5))
        f.answer(FinancialQuestionKind.CONSEQUENCE)
        f.openAndChoose("repair", "repair:pay")
        val expense = f.repo.entries.last().facts.mapNotNull { it.detail as? FactDetail.UnexpectedExpense }.single()
        f.replanAfterExpense(expense.operationId)
        f.send(EngineCommand.StartDeed(offer.id, "goal"))
        val occurrence = f.state.engine!!.currentEvent!!.id
        var quiz = PriceQuizState.create(Random(7))
        val comparisonFacts = mutableListOf<AnalyticsFact>()
        while (!quiz.finished) {
            val question = quiz.question
            comparisonFacts += AnalyticsFact("compare:${quiz.current}", "run", "comparison:$occurrence", "answer:${quiz.current}", 0,
                FactDetail.QuestionAnswer("question:${quiz.current}", 1,
                    AssessmentTask.CompareAmounts(question.leftAmount.toLong(), question.rightAmount.toLong(),
                        if (question.leftIsBigger) ComparisonSide.LEFT else ComparisonSide.RIGHT),
                    seriesClosed = quiz.current == PriceQuizState.QUESTION_COUNT - 1), f.shown("question:${quiz.current}"),
                contextFamily = "money_comparison")
            quiz = quiz.answer(question.leftIsBigger).next()
        }
        // Mirrors the tested UI adapter: shown question amounts, never inferred from the reward.
        f.repo.recordFacts(comparisonFacts)
        f.send(EngineCommand.CompleteDeed(occurrence, checkNotNull(DeedGameScore.fromComparison(quiz))))
        f.send(EngineCommand.DepositSavings(5))
        f.answer(FinancialQuestionKind.TRANSACTION_ACCOUNTING)
        f.send(EngineCommand.FinishDay)
        for (day in 2..7) {
            val event = if (day == 2) "want" else "quiet"
            f.send(EngineCommand.BeginDay("day", listOf(event)))
            f.send(EngineCommand.Feed("meal"))
            f.openAndChoose(event, if (day == 2) "want:later" else "quiet:done")
            if (day == 2) f.send(EngineCommand.DepositSavings(5))
            f.send(EngineCommand.FinishDay)
        }
        f.send(EngineCommand.BeginDay("day", listOf("quiet")))
        val facts = HistoryLearningProjection.facts(f.repo.entries, f.content)
        val profiles = SkillEvaluator().project("run", facts)
        assertEquals(SkillId.entries.toSet(), profiles.filter { it.hasObservations }.map { it.skill }.toSet())
        listOf(SkillId.COMPARE_AMOUNTS, SkillId.PLAN_BUDGET, SkillId.PRIORITIZE_NEEDS,
            SkillId.MAKE_MONEY_LAST, SkillId.DELAY_PURCHASE, SkillId.BUILD_EMERGENCY_FUND,
            SkillId.ADAPT_AFTER_EXPENSE, SkillId.COMPARE_COSTS, SkillId.PLAN_EXTRA_INCOME,
            SkillId.RECONSIDER_DECISION, SkillId.UNDERSTAND_INCOME_AND_EXPENSES).forEach { skill ->
            assertTrue("Missing observed success for $skill", profiles.single { it.skill == skill }.supportedEpisodes > 0)
        }
        // A not-yet-completed goal supplies savings evidence, but does not claim a completed goal cycle.
        assertTrue(profiles.single { it.skill == SkillId.SAVE_FOR_GOAL }.pendingEpisodes > 0)
        assertFalse(f.state.financial.currentPeriod!!.imported)
        assertEquals(0, f.repo.untrackedWrites)
        assertTrue(f.repo.entries.flatMap { it.facts }.none { it.mode != AnalyticsMode.REAL })
    }

    private class Journey {
        private fun event(id: String, type: EventType) = EventDefinition(id, type, id, id, null, null, null, 0, null, null)
        private fun choice(id: String, event: String, position: Int, money: Long) =
            EventChoiceDefinition(id, event, position, id, money, null, null, GoalImpact.NEUTRAL)
        val content = StoryContent(chapters = listOf(ChapterDefinition("chapter", "Chapter", "goal")),
            days = listOf(GameDayDefinition("day", "chapter", 1)),
            events = listOf(event("intro", EventType.STORY), event("work", EventType.EARNING), event("travel", EventType.RANDOM),
                event("want", EventType.WANT), event("repair", EventType.RANDOM), event("quiet", EventType.RANDOM)),
            choices = listOf(choice("intro:done", "intro", 0, 0), choice("work:done", "work", 0, 10),
                choice("travel:walk", "travel", 0, 0), choice("travel:pay", "travel", 1, -4),
                choice("want:buy", "want", 0, -10), choice("want:later", "want", 1, 0),
                choice("repair:pay", "repair", 0, -4), choice("quiet:done", "quiet", 0, 0)),
            goals = listOf(GoalDefinition("goal", "Goal", "")), items = listOf(ItemDefinition("part", "Part", "", priceCoins = 20)),
            requiredItems = listOf(GoalRequiredItem("goal", "part")))
        val repo = JourneyRepository(GameState(PetState("PLAIN", PetVisualState.NORMAL),
            EconomyState(BudgetPlan(0, 0, 0, 0), unallocated = 100,
                planning = BudgetPlanning("initial", BudgetPlanningReason.INITIAL, BudgetPlanningStage.RECEIPT, 100)),
            StoryState(null, null, null, emptyList()), 0, 0, emptyList(), selectedGoalId = "goal"))
        val engine = GameEngine(repo, EventFactory(content, mapOf(
            "intro" to EventPolicy(0), "work" to EventPolicy(1, deedGameKind = DeedGameKind.COMPARISON),
            "travel" to EventPolicy(0, choiceEnergyCosts = mapOf("travel:walk" to 3, "travel:pay" to 0)),
            "want" to EventPolicy(0), "repair" to EventPolicy(0), "quiet" to EventPolicy(0)),
            listOf(MealDefinition("meal", 5, null)), listOf(GoalCampaign("goal", "intro", listOf("part")))),
            EngineRules("ordinary-start-financial-scenario", 5, 3, 1, 100))
        val state get() = repo.state.value
        private var nextId = 0
        fun shown(id: String) = DecisionContext(id, true, true,
            FinancialPosition(state.economy.availableBalance, state.economy.savingsBalance, foodCostUntilWeekEnd(state, 5)),
            alternativeAvailable = true, day = state.engine?.day ?: 1)
        suspend fun send(command: EngineCommand) {
            val id = "decision:${++nextId}"
            val presentation = when (command) {
                is EngineCommand.ConfirmBudget -> "budget:${command.sessionId}:${command.revision}"
                is EngineCommand.AnswerFinancialQuestion -> "question:${command.questionId}"
                else -> id
            }
            val result = engine.dispatch(EngineRequest(id, state.engine?.revision, command, shown(presentation)))
            assertTrue("$command -> $result", result is EngineResult.Applied)
        }
        suspend fun allocate(section: BudgetSection, amount: Long) {
            val planning = state.economy.planning!!
            send(EngineCommand.ChangeBudgetAllocation(planning.id, planning.revision, section, amount = amount))
        }
        suspend fun planInitial() {
            send(EngineCommand.StartBudgetAllocation("initial", 0))
            allocate(BudgetSection.NEEDS, 35); allocate(BudgetSection.WANTS, 10)
            allocate(BudgetSection.SAVINGS, 45); allocate(BudgetSection.RESERVE, 10)
            val planning = state.economy.planning!!
            send(EngineCommand.ConfirmBudget(planning.id, planning.revision, BudgetRevisionReason.INITIAL))
        }
        suspend fun replanAfterExpense(operationId: String) {
            send(EngineCommand.ChangeBudgetAllocation("replan", 0, BudgetSection.WANTS, amount = 0, startManual = true))
            allocate(BudgetSection.NEEDS, 35)
            allocate(BudgetSection.RESERVE, 10)
            allocate(BudgetSection.WANTS, EconomyOperations.allocationRemaining(state.economy))
            val planning = state.economy.planning!!
            send(EngineCommand.ConfirmBudget(planning.id, planning.revision, BudgetRevisionReason.UNEXPECTED_EXPENSE, operationId))
        }
        suspend fun openAndChoose(eventId: String, choiceId: String, priority: String? = null) {
            send(EngineCommand.OpenNextEvent)
            val occurrence = state.engine!!.currentEvent!!
            assertEquals(eventId, occurrence.eventId)
            send(EngineCommand.CompleteEvent(occurrence.id, choiceId, priority))
        }
        suspend fun answer(kind: FinancialQuestionKind) {
            send(EngineCommand.RequestFinancialPractice(kind))
            val question = state.financial.practice!!
            send(EngineCommand.AnswerFinancialQuestion(question.id, question.correctAnswerId))
        }
    }

    private class JourneyRepository(initial: GameState) : GameRepository {
        val state = MutableStateFlow(initial)
        val entries = mutableListOf<AuditEntry>()
        var untrackedWrites = 0
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value
        override suspend fun readHistory() = entries.toList()
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            untrackedWrites++
            return transform(state.value).also { state.value = it }
        }
        override suspend fun commit(request: EngineRequest, context: DecisionContext?, contentFingerprint: String?,
            facts: (GameState, GameState, String, Long) -> List<AnalyticsFact>, transform: (GameState) -> GameState): GameState {
            val before = state.value
            val after = transform(before)
            val sequence = entries.size + 1L
            entries += AuditEntry(request.id, sequence, "run", AuditType.COMMAND, request, context, before, after,
                facts(before, after, "run", sequence), CanonicalLedger.fromTransition(before, after, request),
                contentFingerprint = contentFingerprint)
            state.value = after
            return after
        }
        override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
            sourceGuard?.requireMatches(entries)
            val sequence = entries.size + 1L
            entries += AuditEntry("answers:$sequence", sequence, "run", AuditType.FACTS,
                facts = facts.map { it.copy(sequence = sequence) })
        }
    }
}
