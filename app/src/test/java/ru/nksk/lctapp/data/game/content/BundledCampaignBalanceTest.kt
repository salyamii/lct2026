package ru.nksk.lctapp.data.game.content

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.finance.FinancialMilestone
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.*

/** Catalog regression scenarios, not a target duration or a model of every child's choices. */
class BundledCampaignBalanceTest {
    @Test fun fullCampaignWithPerfectCompletedMiniGames() = runTest {
        Campaign(scoreCap = 100).finish(targetGoals = 5)
    }

    @Test fun fullCampaignWithEightyPercentCompletedMiniGames() = runTest {
        Campaign(scoreCap = 80).finish(targetGoals = 5)
    }

    @Test fun firstGoalWithAtMostHalfOfMaximumMiniGameReward() = runTest {
        Campaign(scoreCap = 50).finish(targetGoals = 1)
    }

    private class Campaign(private val scoreCap: Int) {
        private val catalog = bundledGameCatalog()
        private val initial = createInitialGameState()
        private val repository = MemoryRepository(initial)
        private val content = object : StoryContentRepository {
            override suspend fun read(): StoryContent = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }
        private val session = GameSession(repository, content, catalog, initial)
        private val state get() = repository.value
        private val meal = catalog.meals.single { it.id == "basic-v1" }
        private val order = listOf(STARS_GOAL, TOWER_GOAL, HOME_GOAL, MAP_GOAL, EXPEDITION_GOAL)
        private var sequence = 0
        private var earned = 0L
        private var spent = 0L
        private var weeklyReceipts = 0
        private var completedDeeds = 0
        private val receipts = mutableSetOf<String>()

        // A test player's liquidity choice, derived from catalog prices, not a new economy rule.
        // Keep food plus enough cash for one offered bill; transfer the remaining coins explicitly.
        private val billBuffer = catalog.content.events.maxOf { event ->
            maxOf(0L, -event.moneyDeltaOnStart) + catalog.content.choices.filter { it.eventId == event.id }
                .maxOfOrNull { maxOf(0L, -it.moneyDelta) }.orZero()
        }

        suspend fun finish(targetGoals: Int) {
            assertEquals(100L, initial.economy.availableBalance)
            assertEquals(0L, initial.economy.savingsBalance)
            assertEquals(100L, catalog.rules.weeklyIncome)
            assertEquals(5L, meal.price)
            session.prepare()
            repeat(MAX_COMMANDS) {
                assertTrue(diagnostic(), (state.engine?.day ?: 1) <= MAX_DAYS)
                if (state.completedGoalProjects.size == targetGoals) {
                    assertEquals(order.take(targetGoals), state.completedGoalProjects.map { it.goalId })
                    assertTrue("Scenario must include actual paid mini-games", completedDeeds > 0 && earned > 0)
                    assertTrue("Scenario must exercise the ordinary weekly grant", weeklyReceipts > 0)
                    assertEquals(((state.engine!!.day - 1) / 7), weeklyReceipts)
                    if (targetGoals == 5) assertTrue(catalog.storyProgress(state).campaignComplete)
                    return
                }
                when {
                    state.economy.planning != null -> confirmPlan()
                    state.selectedGoalId == null -> send(session.selectGoalCommand(state, order[state.completedGoalProjects.size]))
                    state.engine?.phase == DayPhase.FINISHED -> send(checkNotNull(session.advanceCommand(state)))
                    state.engine == null -> send(checkNotNull(session.advanceCommand(state)))
                    !state.engine!!.ateToday -> feed()
                    state.engine!!.currentEvent != null -> resolveActiveEvent()
                    else -> continueDay()
                }
            }
            error("Command bound exceeded: ${diagnostic()}")
        }

        private suspend fun continueDay() {
            val goal = checkNotNull(catalog.goals.selectedGoal(state))
            val missing = goal.progress(state, catalog.content).items
                .firstOrNull { item -> state.ownedItems.none { it.itemId == item.id } }
            if (missing != null) {
                if (state.selectedSavingItemId != missing.id) {
                    send(EngineCommand.SelectSavingGoal(goal.goalId, missing.id))
                    return
                }
                val price = checkNotNull(missing.priceCoins)
                val spare = (state.economy.availableBalance - knownFood() - billBuffer).coerceAtLeast(0)
                if (state.economy.savingsBalance < price && spare > 0) {
                    send(EngineCommand.DepositSavings(spare), shownMoney())
                    return
                }
                if (state.economy.savingsBalance >= price) {
                    val buy = EngineCommand.BuyGoalItem(goal.goalId, missing.id)
                    // Saving did not reserve food; if a bill used it, keep living until the next income.
                    if (session.engine.blockReason(state, buy) == null) {
                        send(buy)
                        return
                    }
                }
            }
            // Deferred jobs remain real expiring offers. Only start one the engine permits today.
            val offer = state.engine!!.deeds.filter { it.isAvailable(state.engine!!.day) }
                .sortedWith(compareBy<DeedOffer> { it.expiresDay }.thenBy { catalog.policies.getValue(it.eventId).energyCost })
                .firstOrNull { session.engine.blockReason(state, EngineCommand.StartDeed(it.id)) == null }
            if (offer != null) {
                send(EngineCommand.StartDeed(offer.id, state.selectedGoalId))
                return
            }
            val command = checkNotNull(session.advanceCommand(state)) { diagnostic() }
            when (val blocked = session.engine.blockReason(state, command)) {
                null -> send(command)
                is BlockReason.FinancialPracticeRequired -> recoverFinance(blocked)
                is BlockReason.InsufficientMoney -> withdrawFor(blocked.missing)
                BlockReason.MustEat -> feed()
                else -> error("Cannot advance: $blocked; ${diagnostic()}")
            }
        }

        private suspend fun resolveActiveEvent() {
            val active = checkNotNull(state.engine!!.currentEvent)
            val event = catalog.content.events.single { it.id == active.eventId }
            if (active.origin == EventOrigin.DEED) {
                val score = score(catalog.policies.getValue(active.eventId).deedGameKind!!, scoreCap)
                // Цельные партии (лампы, карта, концы, банки, отличия) завершаются только полностью.
                assertTrue(score.correct == score.attempts || score.correct * 100 <= score.attempts * scoreCap)
                val maximum = catalog.content.choices.single { it.eventId == active.eventId }.moneyDelta
                val before = state.economy.availableBalance
                send(EngineCommand.CompleteDeed(active.id, score))
                assertEquals(score.reward(maximum), state.economy.availableBalance - before)
                completedDeeds++
                return
            }
            if (event.type == EventType.EARNING) {
                val accept = EngineCommand.AcceptDeedProposal(active.id, state.selectedGoalId)
                if (session.engine.blockReason(state, accept) == null) send(accept)
                else send(EngineCommand.DismissDeedProposal(active.id))
                return
            }
            // Decline optional purchases/repairs; for required expenses prefer an affordable
            // effort alternative. Lore and item guards are always checked by the real engine.
            val choices = catalog.content.choices.filter { it.eventId == event.id }.sortedWith(
                compareByDescending<ru.nksk.lctapp.domain.content.EventChoiceDefinition> { it.moneyDelta }
                    .thenByDescending { it.id.endsWith(":skip") }
                    .thenBy { catalog.policies.getValue(event.id).energyFor(it.id) })
            fun completion(choiceId: String): EngineCommand = catalog.policies.getValue(event.id).choiceGameKinds[choiceId]
                ?.let { EngineCommand.CompleteStoryGame(active.id, choiceId, score(it, scoreCap)) }
                ?: EngineCommand.CompleteEvent(active.id, choiceId)
            val allowed = choices.firstOrNull {
                session.engine.blockReason(state, completion(it.id)) == null
            }
            if (allowed != null) {
                send(completion(allowed.id))
                return
            }
            val reasons = choices.map { session.engine.blockReason(state, completion(it.id)) }
            reasons.filterIsInstance<BlockReason.FinancialPracticeRequired>().firstOrNull()?.let {
                recoverFinance(it)
                return
            }
            if (BlockReason.MustEat in reasons) {
                feed()
                return
            }
            val sleep = EngineCommand.FinishDayFromEvent(active.id)
            if (session.engine.blockReason(state, sleep) == null) {
                send(sleep)
                return
            }
            val money = reasons.filterIsInstance<BlockReason.InsufficientMoney>().minByOrNull { it.missing }
            if (money != null && money.missing <= state.economy.savingsBalance) {
                withdrawFor(money.missing)
                return
            }
            error("No payable/feasible choice in ${event.id}: $reasons; ${diagnostic()}")
        }

        private suspend fun feed() {
            val missing = (meal.price - state.economy.availableBalance).coerceAtLeast(0)
            if (missing > 0 && state.economy.savingsBalance >= missing) withdrawFor(missing)
            // The documented fallback is legitimate when both balances are exhausted.
            val selected = if (state.economy.availableBalance >= meal.price) meal else catalog.meals.single { it.price == 0L }
            send(EngineCommand.Feed(selected.id))
        }

        private suspend fun withdrawFor(amount: Long) {
            assertTrue("No liquidity for the mandatory payment: ${diagnostic()}", amount <= state.economy.savingsBalance)
            send(EngineCommand.WithdrawSavings(amount, confirmed = true), shownMoney())
        }

        private suspend fun confirmPlan() {
            fun planning() = checkNotNull(state.economy.planning)
            if (planning().stage == BudgetPlanningStage.RECEIPT)
                send(EngineCommand.StartBudgetAllocation(planning().id, planning().revision))
            // Revise intentions for all remaining available money; neither drop old leftovers
            // nor count the existing savings balance as new spendable income.
            val basis = EconomyOperations.planningAmount(state.economy)
            val needs = EconomyOperations.minimumNeeds(state.economy, knownFood())
            val reserve = minOf(billBuffer, basis - needs)
            for (section in BudgetSection.entries.filter { it != BudgetSection.NEEDS }) {
                if (state.economy.displayPlan.amount(section) != 0L)
                    send(EngineCommand.ChangeBudgetAllocation(planning().id, planning().revision, section, amount = 0))
            }
            send(EngineCommand.ChangeBudgetAllocation(planning().id, planning().revision, BudgetSection.NEEDS, amount = needs))
            send(EngineCommand.ChangeBudgetAllocation(planning().id, planning().revision, BudgetSection.RESERVE, amount = reserve))
            send(EngineCommand.ChangeBudgetAllocation(planning().id, planning().revision, BudgetSection.SAVINGS, amount = basis - needs - reserve))
            val money = state.economy.balance
            send(EngineCommand.ConfirmBudget(planning().id, planning().revision), shownMoney())
            assertEquals(basis, state.economy.plan.total)
            assertEquals(money, state.economy.balance)
        }

        private suspend fun recoverFinance(blocked: BlockReason.FinancialPracticeRequired) {
            if (FinancialMilestone.PROVIDE_NEEDS in blocked.missing) feed()
            if (FinancialMilestone.SAVE_FOR_GOAL in blocked.missing) answerPractice(FinancialQuestionKind.SAVING_PRACTICE)
            if (FinancialMilestone.REVIEW_PLAN in blocked.missing) {
                answerPractice(FinancialQuestionKind.PLAN_REVIEW)
                if (FinancialMilestone.REVIEW_PLAN in state.financial.currentPeriod!!.missingMilestones) {
                    // This repository intentionally has no audit. Guided recovery must include
                    // a new realistic confirmed plan, not invent category history or deposits.
                    send(EngineCommand.ChangeBudgetAllocation("recovery-$sequence", 0, BudgetSection.RESERVE,
                        amount = 0, startManual = true))
                    confirmPlan()
                }
            }
            assertTrue("Practice did not unblock the period: ${diagnostic()}", state.financial.currentPeriod!!.missingMilestones.isEmpty())
        }

        private suspend fun answerPractice(kind: FinancialQuestionKind) {
            send(EngineCommand.RequestFinancialPractice(kind))
            val question = checkNotNull(state.financial.practice)
            assertEquals(kind, question.kind)
            val balance = state.economy.balance
            send(EngineCommand.AnswerFinancialQuestion(question.id, question.correctAnswerId))
            send(EngineCommand.CloseFinancialPractice)
            assertEquals("A practice answer must not grant money", balance, state.economy.balance)
        }

        private fun knownFood() = foodCostUntilWeekEnd(state, meal.price)
        private fun shownMoney() = DecisionContext(presentationId = "campaign-$sequence",
            informationPresented = true, complete = true, alternativeAvailable = true,
            before = FinancialPosition(state.economy.availableBalance, state.economy.savingsBalance, knownFood()))

        private suspend fun send(command: EngineCommand, context: DecisionContext? = null) {
            check(sequence < MAX_COMMANDS) { "Command bound exceeded: ${diagnostic()}" }
            val result = session.dispatch(EngineRequest("balance-$scoreCap-${++sequence}", state.engine?.revision, command, context))
            check(result is EngineResult.Applied) { "$command: $result; ${diagnostic()}" }
            assertTrue(state.economy.availableBalance >= 0 && state.economy.savingsBalance >= 0)
            state.engine?.journal.orEmpty().filter { receipts.add(it.id) }.forEach { receipt ->
                if (receipt.kind == DayJournalKind.WEEKLY_INCOME) {
                    assertEquals(100L, receipt.moneyDelta)
                    weeklyReceipts++
                } else if (receipt.moneyDelta > 0) earned += receipt.moneyDelta
                if (receipt.moneyDelta < 0) spent -= receipt.moneyDelta
            }
            assertEquals("All money must come from the initial wallet, real income or receipts", 100L + weeklyReceipts * 100L + earned - spent, state.economy.balance)
        }

        private fun diagnostic() = "scoreCap=$scoreCap, command=$sequence, day=${state.engine?.day}, " +
            "goals=${state.completedGoalProjects.size}, available=${state.economy.availableBalance}, " +
            "savings=${state.economy.savingsBalance}, event=${state.engine?.currentEvent}, " +
            "milestones=${state.financial.currentPeriod?.missingMilestones}"
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        private val flow = MutableStateFlow(initial)
        val value get() = flow.value
        override fun observe() = flow
        override suspend fun read() = value
        override suspend fun initializeIfAbsent(initial: GameState) = value
        override suspend fun update(transform: (GameState) -> GameState) = transform(value).also { flow.value = it }
    }

    private companion object {
        const val MAX_DAYS = 200
        const val MAX_COMMANDS = 20_000
        fun Long?.orZero() = this ?: 0L

        fun score(kind: DeedGameKind, cap: Int): DeedGameScore = checkNotNull(when (kind) {
            // Five-round games cannot represent exactly 50%; round down to 2/5, never up to 3/5.
            DeedGameKind.PRECISION -> DeedGameScore.fromPrecision(TargetStopState(10,
                round = TargetStopState.ROUNDS, hits = TargetStopState.ROUNDS * cap / 100, lastHit = true))
            DeedGameKind.COMPARISON -> DeedGameScore.fromComparison(PriceQuizState.create().copy(
                current = PriceQuizState.QUESTION_COUNT, correctAnswers = PriceQuizState.QUESTION_COUNT * cap / 100))
            DeedGameKind.MEMORY -> DeedGameScore.fromMemory(MemoryState((0 until MemoryState.PAIRS).flatMap { listOf(it, it) })
                .let {
                    val attempts = (MemoryState.PAIRS * 100 + cap - 1) / cap
                    it.copy(matched = it.faces.indices.toSet(), moves = attempts,
                        recallMistakes = attempts - MemoryState.PAIRS)
                })
            // Лампы и концы не делятся на доли: партия либо решена, либо нет.
            DeedGameKind.LIGHTS -> DeedGameScore.fromLights(LightsState(
                List(LightsState.SIZE * LightsState.SIZE) { false }, moves = 1))
            DeedGameKind.SEQUENCE -> DeedGameScore.fromSequence(SequenceState(
                sequence = List(SequenceState.FIRST_ROUND_LENGTH) { 0 },
                round = SequenceState.ROUNDS,
                correct = SequenceState.ROUNDS * cap / 100,
                lastCorrect = true,
            ))
            DeedGameKind.PIPES -> DeedGameScore.fromPipes(PipesState(PipesState.PUZZLE, paths = solvedPipePaths()))
            // All differences are found, but missed cells lower the completed score.
            DeedGameKind.DIFFERENCES -> DeedGameScore.fromDifferences(DifferencesState.create().let { board ->
                board.differences.fold(board) { state, cell -> state.tap(cell) }
                    .copy(taps = (DifferencesState.DIFF_COUNT * 100 + cap - 1) / cap)
            })
            // Ограниченное число ящиков округляет долю вниз, как PRECISION выше.
            DeedGameKind.STACKING -> DeedGameScore.fromStacking(StackingState(
                locked = List(StackingState.ROUNDS * cap / 100) { StackedBlock(0, StackingState.START_WIDTH) },
                blockWidth = StackingState.START_WIDTH,
                placed = StackingState.ROUNDS * cap / 100,
                finished = true,
            ))
        })

        private fun solvedPipePaths(): Map<Int, List<Int>> = mapOf(
            0 to listOf(0, 5, 10, 15, 20),
            1 to listOf(4, 9, 14, 19, 24),
            2 to listOf(11, 6, 7, 8, 13),
        )
    }
}
