package ru.nksk.lctapp.domain.history

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.analytics.FactDetail.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class HistoryLearningProjectionTest {
    private val initial = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(0, 0, 0, 0), availableBalance = 70, savingsBalance = 30),
        StoryState(null, null, null, emptyList()), 0, 0, emptyList())
    private fun context(day: Int = 1, needs: Long = 35) = DecisionContext("shown-$day", true, true,
        FinancialPosition(70, 30, needs), FinancialPosition(70, 30, needs), true, day = day)

    @Test fun compactEvidenceMatchesLegacyForLazyShuffledDuplicateAndDamagedHistories() {
        val seeds = listOf(week(), week(freeFood = true), week(bakeryDay = 3), repairHistory(),
            repairHistory(planAfterPresentation = true), repairHistory(originalPresentationMissing = true),
            repairHistory(reopenAfterPlan = true))
        for (history in seeds) repeat(10) { variation ->
            val random = Random(variation)
            val changed = if (variation == 0) history else history.mapNotNull { entry ->
                when (random.nextInt(7)) {
                    0 -> null
                    1 -> entry.copy(request = entry.request?.copy(demoMode = true))
                    2 -> entry.copy(before = entry.before?.let { it.copy(pet = it.pet.copy(name = "Changed checkpoint")) })
                    3 -> entry.copy(type = AuditType.RESTORED)
                    4 -> entry.copy(operations = emptyList())
                    else -> entry
                }
            }
            val anotherRun = changed.map { entry -> entry.copy(id = "second:${entry.id}", runId = "second-run",
                facts = entry.facts.map { it.copy(eventId = "second:${it.eventId}", gameRunId = "second-run") }) }
            val combined = (changed + anotherRun + changed.take(2)).shuffled(random)
            val encoded = combined.map(HistoryCodec::encode)
            var reads = 0
            val lazy = object : AbstractList<AuditEntry>() {
                override val size get() = encoded.size
                override fun get(index: Int): AuditEntry {
                    reads++
                    return HistoryCodec.decodeEntry(encoded[index])
                }
            }
            assertEquals("Variation $variation", LegacyHistoryLearningProjection.facts(combined),
                HistoryLearningProjection.facts(lazy))
            // Metadata and evidence each decode a unique row once; duplicates only revisit their original.
            assertEquals(encoded.size * 2, reads)
        }
    }

    @Test fun compactEvidenceStillRejectsConflictingIdentityAndSequence() {
        val history = week()
        val first = history.first()
        for (conflict in listOf(first.copy(after = first.after!!.copy(satiety = 1)),
            first.copy(id = "different-identity-same-sequence"))) {
            val input = history + conflict
            val oldFailure = assertThrows(IllegalArgumentException::class.java) { LegacyHistoryLearningProjection.facts(input) }
            val newFailure = assertThrows(IllegalArgumentException::class.java) { HistoryLearningProjection.facts(input) }
            assertEquals(oldFailure.message, newFailure.message)
        }
    }

    @Test fun completeWeekClosesIncomeAndReserveOnceAndIsReplayable() {
        val history = week()
        val facts = HistoryLearningProjection.facts(history)
        assertEquals(facts, HistoryLearningProjection.facts(history.reversed() + history.first()))
        val interval = facts.single { it.detail is IncomeWindow }.detail as IncomeWindow
        assertTrue(interval.historyComplete)
        assertTrue(interval.knownNeedsMet)
        assertEquals(listOf("plan"), interval.managedDecisionIds)
        val reserve = facts.filter { (it.detail as? ReserveDecision)?.intervalClosed == true }.single()
        assertEquals(10L, (reserve.detail as ReserveDecision).remainingAmount)
        assertEquals(0L, reserve.detail.usedForUnexpectedExpense)
        val observed = SkillEvaluator().evaluate(facts)
        assertEquals(ObservationOutcome.SUPPORTED, observed.single { it.skill == SkillId.MAKE_MONEY_LAST }.outcome)
        assertEquals(ObservationOutcome.SUPPORTED, observed.single { it.skill == SkillId.BUILD_EMERGENCY_FUND }.outcome)
    }

    @Test fun missingDayCannotBecomeAPlanningFailureOrSuccess() {
        val facts = HistoryLearningProjection.facts(week().filterNot { it.id == "finish-4" })
        val outcome = SkillEvaluator().evaluate(facts).single { it.skill == SkillId.MAKE_MONEY_LAST }
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, outcome.outcome)
        assertFalse((facts.single { it.detail is IncomeWindow }.detail as IncomeWindow).historyComplete)
    }

    @Test fun freeFallbackAloneDoesNotProveBudgetCoverageOrDifficulty() {
        val observed = SkillEvaluator().evaluate(HistoryLearningProjection.facts(week(freeFood = true)))
            .single { it.skill == SkillId.MAKE_MONEY_LAST }
        assertEquals(ObservationOutcome.NEUTRAL, observed.outcome)
    }

    @Test fun bakeryChoiceFinancesTheDaysFoodWithItsSingleActualPurchaseReceipt() {
        val history = week(bakeryDay = 3)
        val purchase = history.single { it.id == "feed-3" }
        assertEquals(1, purchase.operations.size)
        assertEquals(6L, purchase.operations.single().amount)
        assertFalse(purchase.after!!.engine!!.journal.any { it.kind == DayJournalKind.MEAL })
        val facts = HistoryLearningProjection.facts(history)
        val window = facts.single { it.detail is IncomeWindow }.detail as IncomeWindow
        assertTrue(window.historyComplete)
        assertTrue(window.knownNeedsMet)
        assertEquals(ObservationOutcome.SUPPORTED, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.MAKE_MONEY_LAST }.outcome)
    }

    @Test fun satiatedSnapshotWithoutTheMatchingExpenseDoesNotProvePaidFood() {
        val history = week(bakeryDay = 3).map {
            if (it.id == "feed-3") it.copy(operations = emptyList()) else it
        }
        val facts = HistoryLearningProjection.facts(history)
        assertFalse((facts.single { it.detail is IncomeWindow }.detail as IncomeWindow).historyComplete)
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.MAKE_MONEY_LAST }.outcome)
    }

    @Test fun consecutiveAuditNumbersDoNotHideADisconnectedSnapshot() {
        val history = week().map { if (it.id == "finish-4") it.copy(before = it.before!!.copy(satiety = 1)) else it }
        val facts = HistoryLearningProjection.facts(history)
        assertFalse((facts.single { it.detail is IncomeWindow }.detail as IncomeWindow).historyComplete)
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.MAKE_MONEY_LAST }.outcome)
    }

    @Test fun completedWorkNeedsLaterInformedAccountingAction() {
        val history = mutableListOf<AuditEntry>()
        history += entry("start", 1, EngineCommand.StartDeed("offer"),
            listOf(fact("plan", 1, "earning", EarningPlan("offer", "goal", 1, 2, 1, 5, 10))))
        history += entry("earned", 2, EngineCommand.RenamePet("Лис", "Рыжик"),
            listOf(fact("reward", 2, "earning", EarningCompleted("offer", 1, 7, false))))
        val incomplete = SkillEvaluator().evaluate(HistoryLearningProjection.facts(history)).single()
        assertEquals(EpisodeCompletion.PENDING, incomplete.completion)
        history += entry("confirmed", 3, EngineCommand.ConfirmBudget("draft", 0),
            listOf(fact("confirm", 3, "budget", Interaction("ConfirmBudget"))))
        val supported = SkillEvaluator().evaluate(HistoryLearningProjection.facts(history)).single()
        assertEquals(ObservationOutcome.SUPPORTED, supported.outcome)
        assertEquals(7L, supported.measures["actualReward"])
    }

    @Test fun quizDoesNotBecomeApplicationWithoutComparableRealContext() {
        val answer = fact("answer", 1, "quiz", QuestionAnswer("q", 1,
            AssessmentTask.ExplainCause("less", "less", listOf("old-purchase")), comparisonFamily = "optional_purchase"))
            .copy(learningContext = LearningContext.COUNTERFACTUAL)
        val later = fact("purchase", 2, "shop", OptionalPurchase("hat", 5, false))
            .copy(contextFamily = "optional_purchase", context = context().copy(complete = false))
        val history = listOf(
            AuditEntry("q", 1, "run", AuditType.FACTS, facts = listOf(answer)),
            entry("shop", 2, EngineCommand.RenamePet("Лис", "Рыжик"), listOf(later)),
        )
        val insufficient = SkillEvaluator().evaluate(HistoryLearningProjection.facts(history))
            .single { it.skill == SkillId.RECONSIDER_DECISION }
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, insufficient.outcome)
        val complete = history.dropLast(1) + history.last().copy(facts = listOf(later.copy(actionId = "shop", context = context())))
        val supported = SkillEvaluator().evaluate(HistoryLearningProjection.facts(complete))
            .single { it.skill == SkillId.RECONSIDER_DECISION }
        assertEquals(ObservationOutcome.SUPPORTED, supported.outcome)
    }

    @Test fun anUnseenOrUnaffordableFirstOpportunityDoesNotConsumeALaterObservedApplication() {
        val answer = fact("answer", 1, "quiz", QuestionAnswer("q", 1,
            AssessmentTask.ExplainCause("less", "less", listOf("old-purchase")), comparisonFamily = "optional_purchase"))
        val unseen = fact("unseen", 2, "shop-1", OptionalPurchase("hat", 5, false))
            .copy(contextFamily = "optional_purchase", context = context().copy(complete = false))
        val forced = fact("forced", 3, "shop-2", OptionalPurchase("hat", 100, false))
            .copy(contextFamily = "optional_purchase")
        val voluntary = fact("voluntary", 4, "shop-3", OptionalPurchase("hat", 5, false))
            .copy(contextFamily = "optional_purchase")
        val history = listOf(AuditEntry("quiz", 1, "run", AuditType.FACTS, facts = listOf(answer)),
            entry("shop-1", 2, EngineCommand.OpenNextEvent, listOf(unseen)),
            entry("shop-2", 3, EngineCommand.OpenNextEvent, listOf(forced)),
            entry("shop-3", 4, EngineCommand.OpenNextEvent, listOf(voluntary)))
        val facts = HistoryLearningProjection.facts(history)
        assertEquals("shop-3", (facts.single { it.detail is ComparableApplication }.detail as ComparableApplication).sourceActionId)
        assertEquals(ObservationOutcome.SUPPORTED, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.RECONSIDER_DECISION }.outcome)
    }

    @Test fun reserveUseIsLinkedToTheRealExpenseAndSurvivesAReplan() {
        val history = repairHistory()
        val facts = HistoryLearningProjection.facts(history)
        val use = facts.mapNotNull { it.detail as? ReserveDecision }.single { it.usedForUnexpectedExpense > 0 }
        assertEquals(listOf(ReserveApplication("repair-operation", 4)), use.applications)
        assertEquals(6L, use.remainingAmount)
        val result = SkillEvaluator().evaluate(facts).single { it.skill == SkillId.BUILD_EMERGENCY_FUND }
        assertEquals(ObservationOutcome.SUPPORTED, result.outcome)
        assertEquals(ObservationReason.RESERVE_USED, result.reason)
        assertEquals(1L, result.measures["attributedExpenses"])
        assertEquals(facts, HistoryLearningProjection.facts(history + history.single { it.id == "repair" }))
    }

    @Test fun directAndMixedGoalPaymentsKeepKnownReserveHistoryCompleteWithoutInventingAnEmergency() {
        for (savings in listOf(0L, 7L)) {
            val opening = initial.copy(economy = EconomyState(BudgetPlan(35, 15, 10, 10), savingsBalance = savings),
                engine = EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, true, null,
                    70 + savings, emptyList(), emptyList()))
            val purchased = opening.copy(economy = EconomyOperations.purchaseGoal(opening.economy, 20),
                ownedItems = listOf(ru.nksk.lctapp.domain.game.OwnedItem("buy:purchase", "map")),
                engine = opening.engine!!.copy(revision = 1, steps = 1,
                    journal = listOf(DayJournalEntry("buy:journal:0", DayJournalKind.ITEM_PURCHASE, "map", -20))))
            fun command(id: String, sequence: Long, before: GameState, after: GameState,
                action: EngineCommand, detail: FactDetail, episode: String): AuditEntry {
                val request = EngineRequest(id, before.engine?.revision, action)
                val shown = context().copy(
                    before = FinancialPosition(before.economy.availableBalance, before.economy.savingsBalance, 35),
                    after = FinancialPosition(after.economy.availableBalance, after.economy.savingsBalance, 35))
                return AuditEntry(id, sequence, "run", AuditType.COMMAND, request, before = before, after = after,
                    facts = listOf(fact(id, sequence, episode, detail).copy(context = shown)),
                    operations = CanonicalLedger.fromTransition(before, after, request))
            }
            val history = listOf(
                command("plan", 1, opening, opening, EngineCommand.ConfirmBudget("draft", 0),
                    ReserveDecision("plan", 10, 10, 0, false), "reserve:plan"),
                command("buy", 2, opening, purchased, EngineCommand.BuyGoalItem("goal", "map"),
                    Interaction("BuyGoalItem"), "purchase"),
                command("replan", 3, purchased, purchased, EngineCommand.ConfirmBudget("next-draft", 0),
                    ReserveDecision("next-plan", 0, 0, 0, false), "reserve:next"),
            )
            val facts = HistoryLearningProjection.facts(history)
            val closed = facts.single { (it.detail as? ReserveDecision)?.let { decision ->
                decision.intentionId == "plan" && decision.intervalClosed
            } == true }
            assertTrue("Known goal payment must not make reserve evidence incomplete", closed.context.complete)
            val reserve = closed.detail as ReserveDecision
            assertEquals(10L, reserve.remainingAmount)
            assertEquals(0L, reserve.usedForUnexpectedExpense)
            assertTrue(reserve.applications.isEmpty())
            assertTrue(facts.none { it.detail is UnexpectedExpense })
        }
    }

    @Test fun reserveCreatedAfterSeeingTheBillIsNotAnticipatoryReserveEvidence() {
        val facts = HistoryLearningProjection.facts(repairHistory(planAfterPresentation = true))
        val closed = facts.mapNotNull { it.detail as? ReserveDecision }.single { it.intervalClosed }
        assertEquals(0L, closed.usedForUnexpectedExpense)
        assertTrue(closed.applications.isEmpty())
        assertEquals(6L, closed.remainingAmount)
        assertEquals(ObservationOutcome.NEUTRAL, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.BUILD_EMERGENCY_FUND }.outcome)
    }

    @Test fun unknownOriginalPresentationCannotBecomePositiveReserveEvidence() {
        val facts = HistoryLearningProjection.facts(repairHistory(originalPresentationMissing = true))
        assertTrue(facts.mapNotNull { it.detail as? ReserveDecision }.all { it.applications.isEmpty() })
        assertEquals(ObservationOutcome.INSUFFICIENT_DATA, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.BUILD_EMERGENCY_FUND }.outcome)
    }

    @Test fun reopeningTheSameOccurrenceDoesNotResetWhenTheBillBecameKnown() {
        val facts = HistoryLearningProjection.facts(repairHistory(planAfterPresentation = true, reopenAfterPlan = true))
        assertTrue(facts.mapNotNull { it.detail as? ReserveDecision }.all { it.applications.isEmpty() })
        assertEquals(ObservationOutcome.NEUTRAL, SkillEvaluator().evaluate(facts)
            .single { it.skill == SkillId.BUILD_EMERGENCY_FUND }.outcome)
    }

    @Test fun newIncomeDoesNotRestoreReserveLostToAnOptionalPurchase() {
        val opening = initial.copy(economy = initial.economy.copy(availableBalance = 50))
        val spent = opening.copy(economy = opening.economy.copy(availableBalance = 30))
        val replenished = spent.copy(economy = spent.economy.copy(availableBalance = 130))
        fun shown(before: Long, after: Long) = context().copy(before = FinancialPosition(before, 30, 35), after = FinancialPosition(after, 30, 35))
        fun command(id: String, sequence: Long, before: GameState, after: GameState, detail: FactDetail,
            episode: String, operations: List<LedgerEntry> = emptyList()) = AuditEntry(id, sequence, "run", AuditType.COMMAND,
            EngineRequest(id, null, EngineCommand.OpenNextEvent), before = before, after = after,
            facts = listOf(fact(id, sequence, episode, detail).copy(context = shown(before.economy.availableBalance, after.economy.availableBalance))),
            operations = operations)
        val history = listOf(
            command("plan", 1, opening, opening, ReserveDecision("p", 10, 10, 0, false), "reserve:week1"),
            command("want", 2, opening, spent, OptionalPurchase("hat", 20, true), "want",
                listOf(LedgerEntry("hat", LedgerKind.AVAILABLE_EXPENSE, 20))),
            command("income", 3, spent, replenished, Interaction("weekly-income"), "income",
                listOf(LedgerEntry("income", LedgerKind.INCOME, 100))),
            command("replan", 4, replenished, replenished, ReserveDecision("p2", 0, 0, 0, false), "reserve:week1"))
        val result = SkillEvaluator().evaluate(HistoryLearningProjection.facts(history))
            .single { it.skill == SkillId.BUILD_EMERGENCY_FUND }
        assertEquals(ObservationOutcome.NEUTRAL, result.outcome)
    }

    private fun week(freeFood: Boolean = false, bakeryDay: Int? = null): List<AuditEntry> {
        val entries = mutableListOf<AuditEntry>()
        var state = initial
        fun append(id: String, command: EngineCommand, next: GameState, details: List<Pair<String, FactDetail>>, day: Int, needs: Long) {
            val sequence = entries.size.toLong() + 1
            val request = EngineRequest(id, state.engine?.revision, command)
            val facts = details.mapIndexed { index, (episode, detail) -> fact("$id:$index", sequence, episode, detail)
                .copy(actionId = id, context = context(day, needs).copy(
                    before = FinancialPosition(state.economy.availableBalance, state.economy.savingsBalance, needs),
                    after = FinancialPosition(next.economy.availableBalance, next.economy.savingsBalance, needs))) }
            entries += AuditEntry(id, sequence, "run", AuditType.COMMAND, request, before = state, after = next, facts = facts,
                operations = CanonicalLedger.fromTransition(state, next, request))
            state = next
        }
        for (day in 1..8) {
            val begun = state.copy(engine = EngineState("rules", day.toLong(), day, DayPhase.RUNNING, 0, 5, false,
                null, 100, emptyList(), emptyList()))
            append("begin-$day", EngineCommand.BeginDay("content-day", emptyList()), begun,
                listOf("day-$day" to Interaction("BeginDay")), day, if (day == 8) 35 else (8L - day) * 5)
            if (day == 1) append("plan", EngineCommand.ConfirmBudget("draft", 0), state,
                listOf("plan" to BudgetConfirmed("plan", 1, 70, 35, 15, 10, 10, BudgetRevisionCause.INITIAL),
                    "reserve:plan" to ReserveDecision("plan", 10, 10, 0, false)), 1, 35)
            if (day != 8) {
                val bakery = day == bakeryDay
                val price = if (freeFood) 0L else if (bakery) 6L else 5L
                if (bakery) append("show-bakery", EngineCommand.OpenNextEvent, state.copy(
                    story = state.story.copy(activeEventId = "bakery"),
                    engine = state.engine!!.copy(events = listOf(EventOccurrence("bakery-offer", "bakery", EventOrigin.SCHEDULE, EventStatus.ACTIVE)))),
                    listOf("day-$day" to Interaction("OpenNextEvent")), day, (8L - day) * 5)
                val command = if (bakery) EngineCommand.CompleteEvent("bakery-offer", "bakery:buy") else EngineCommand.Feed("meal")
                val receipt = if (bakery) DayJournalEntry("meal-$day", DayJournalKind.EVENT_CHOICE, "bakery:buy", -price)
                    else DayJournalEntry("meal-$day", DayJournalKind.MEAL, "meal", -price)
                append("feed-$day", command, state.copy(
                    economy = state.economy.copy(availableBalance = state.economy.availableBalance - price),
                    story = state.story.copy(activeEventId = null),
                    engine = state.engine!!.copy(ateToday = true,
                        events = state.engine!!.events.map { it.copy(status = EventStatus.COMPLETED) }, journal = listOf(receipt))),
                    listOf("day-$day" to if (bakery) OptionalPurchase("bakery", price, true) else Interaction("Feed")), day, (7L - day) * 5)
            }
            if (day != 8) append("finish-$day", EngineCommand.FinishDay,
                state.copy(engine = state.engine!!.copy(phase = DayPhase.FINISHED, ateToday = true)),
                listOf("day-$day" to Interaction("FinishDay")), day, (7L - day) * 5)
        }
        return entries
    }

    private fun repairHistory(planAfterPresentation: Boolean = false, originalPresentationMissing: Boolean = false,
        reopenAfterPlan: Boolean = false): List<AuditEntry> {
        val entries = mutableListOf<AuditEntry>()
        var state = initial.copy(
            story = initial.story.copy(activeEventId = if (originalPresentationMissing) "repair-event" else null),
            engine = EngineState("rules", 0, 1, DayPhase.RUNNING, 0, 5, false, null, 70,
                listOf(EventOccurrence("repair-offer", "repair-event", EventOrigin.SCHEDULE,
                    if (originalPresentationMissing) EventStatus.ACTIVE else EventStatus.PENDING)), emptyList()))
        fun append(id: String, command: EngineCommand, next: GameState, detail: FactDetail, episode: String = id) {
            val sequence = entries.size.toLong() + 1
            val request = EngineRequest(id, state.engine?.revision, command)
            val observed = fact(id, sequence, episode, detail).copy(context = context().copy(
                before = FinancialPosition(state.economy.availableBalance, 30, 35),
                after = FinancialPosition(next.economy.availableBalance, 30, 35)))
            entries += AuditEntry(id, sequence, "run", AuditType.COMMAND, request, before = state, after = next,
                facts = listOf(observed), operations = CanonicalLedger.fromTransition(state, next, request))
            state = next
        }
        fun plan() = append("plan", EngineCommand.ConfirmBudget("p", 0), state,
            ReserveDecision("plan", 10, 10, 0, false), "reserve:week1")
        if (!planAfterPresentation) plan()
        if (!originalPresentationMissing) append("show", EngineCommand.OpenNextEvent, state.copy(
            story = state.story.copy(activeEventId = "repair-event"), engine = state.engine!!.copy(
                events = state.engine!!.events.map { it.copy(status = EventStatus.ACTIVE) })), Interaction("OpenNextEvent"))
        if (reopenAfterPlan) append("pause", EngineCommand.PauseEvent("repair-offer"), state.copy(
            story = state.story.copy(activeEventId = null), engine = state.engine!!.copy(
                events = state.engine!!.events.map { it.copy(status = EventStatus.PAUSED) })), Interaction("PauseEvent"))
        if (planAfterPresentation) plan()
        if (reopenAfterPlan) append("resume", EngineCommand.OpenNextEvent, state.copy(
            story = state.story.copy(activeEventId = "repair-event"), engine = state.engine!!.copy(
                events = state.engine!!.events.map { it.copy(status = EventStatus.ACTIVE) })), Interaction("OpenNextEvent"))
        append("repair", EngineCommand.CompleteEvent("repair-offer", "repair:pay"), state.copy(
            story = state.story.copy(activeEventId = null), economy = state.economy.copy(availableBalance = 66),
            engine = state.engine!!.copy(events = state.engine!!.events.map { it.copy(status = EventStatus.COMPLETED) },
                journal = listOf(DayJournalEntry("repair-operation", DayJournalKind.EVENT_CHOICE, "repair:pay", -4)))),
            UnexpectedExpense("repair-operation", 4, false))
        append("replan", EngineCommand.ConfirmBudget("p2", 0), state,
            ReserveDecision("new-plan", 0, 0, 0, false), "reserve:week1")
        return entries
    }

    private fun fact(id: String, sequence: Long, episode: String, detail: FactDetail) =
        AnalyticsFact(id, "run", episode, id, sequence, detail, context(), contextFamily = "test")

    private fun entry(id: String, sequence: Long, command: EngineCommand, facts: List<AnalyticsFact>) =
        AuditEntry(id, sequence, "run", AuditType.COMMAND, EngineRequest(id, null, command),
            before = initial, after = initial, facts = facts.map { it.copy(actionId = id) })
}
