package ru.nksk.lctapp.domain.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.analytics.FactDetail.*
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

class SkillExplanationContextsTest {
    private val eventId = "event-opaque-42"
    private val choiceId = "choice-opaque-81"
    private val title = "Лямка не выдержала"
    private val content = StoryContent(
        events = listOf(event(eventId, title)),
        choices = listOf(EventChoiceDefinition(choiceId, eventId, 0, "Оплатить помощь", -4,
            null, null, GoalImpact.NEUTRAL)),
    )

    @Test fun reserveContextUsesTheActualExpenseDayAndTitleRatherThanThePlanningDay() {
        val data = reserveFixture()
        assertEquals(1, data.closed().context.day)
        assertEquals(SkillEventContext(3, title), data.context())
    }

    @Test fun closedReserveEvidenceCanExistOnlyInTheFullProjectedFacts() {
        val data = reserveFixture()
        assertEquals(false, data.history.flatMap { it.facts }.any { it.eventId == "derived-reserve" })
        assertEquals(SkillEventContext(3, title), data.context())
        assertNull(data.copy(facts = data.history.flatMap { it.facts }).context())
    }

    @Test fun eventStartReceiptResolvesAnAutomaticExpenseWithoutRequiringAChoice() {
        val expense = expenseEntry(journalKind = DayJournalKind.EVENT_START)
        val data = reserveFixture(expenses = listOf(expense))
        assertEquals(SkillEventContext(3, title), data.context())
    }

    @Test fun partialReserveContributionStillIdentifiesTheSameActualExpense() {
        val data = reserveFixture(attributions = listOf(ReserveApplication("receipt-opaque", 2)))
        assertEquals(4L, data.history.last().operations.single().amount)
        assertEquals(SkillEventContext(3, title), data.context())
    }

    @Test fun aMissingExpenseFactDayCanUseTheRecordedCalendarDay() {
        val data = reserveFixture()
        val expense = data.history.last()
        assertEquals(SkillEventContext(3, title), data.withEntry(expense.copy(facts = expense.facts.map {
            it.copy(context = it.context.copy(day = null))
        })).context())
    }

    @Test fun sourceAndHistoryOrderingDoNotChooseADifferentContext() {
        val data = reserveFixture()
        val reordered = data.copy(history = data.history.reversed(), facts = data.facts.reversed(),
            observation = data.observation.copy(sourceEventIds = data.observation.sourceEventIds.reversed()))
        assertEquals(SkillEventContext(3, title), reordered.context())
    }

    @Test fun transportSequenceChangesDoNotBreakTheIdentityLinks() {
        val data = reserveFixture()
        // Upload ordinals can differ from local audit sequence numbers.
        val transported = data.copy(facts = data.facts.mapIndexed { index, fact ->
            fact.copy(sequence = 500L + index)
        })
        assertEquals(SkillEventContext(3, title), transported.context())
    }

    @Test fun missingForeignOrConflictingSourcesDoNotBorrowAContext() {
        val data = reserveFixture()
        assertNull(data.copy(observation = data.observation.copy(sourceEventIds = emptyList())).context())
        assertNull(data.copy(observation = data.observation.copy(
            sourceEventIds = data.observation.sourceEventIds + "unknown-source")).context())
        assertNull(data.withClosed(data.closed().copy(gameRunId = "another-run")).context())
        assertNull(data.withClosed(data.closed().copy(episodeId = "another-episode")).context())
        assertNull(data.copy(facts = data.facts + data.closed().copy(
            context = data.closed().context.copy(day = 7))).context())
    }

    @Test fun anUnfinishedIneligibleOrDifferentReserveObservationGetsNoExpenseContext() {
        val data = reserveFixture()
        val unsupported = listOf(
            data.observation.copy(completion = EpisodeCompletion.PENDING),
            data.observation.copy(eligibility = ObservationEligibility.NOT_ELIGIBLE),
            data.observation.copy(outcome = ObservationOutcome.DIFFICULTY),
            data.observation.copy(reason = ObservationReason.RESERVE_PRESERVED),
        )
        unsupported.forEach { assertNull(it.toString(), data.copy(observation = it).context()) }
    }

    @Test fun incompleteOrUnclosedReserveAccountingCannotNameAnExpense() {
        val data = reserveFixture()
        assertNull(data.withClosed(data.closed().copy(context = data.closed().context.copy(complete = false))).context())
        val detail = data.closed().detail as ReserveDecision
        listOf(
            detail.copy(intervalClosed = false),
            detail.copy(applications = emptyList()),
            detail.copy(usedForUnexpectedExpense = detail.usedForUnexpectedExpense + 1),
            detail.copy(remainingAmount = detail.declaredAmount),
            detail.copy(applications = listOf(ReserveApplication("receipt-opaque", 5)),
                usedForUnexpectedExpense = 5, remainingAmount = 5),
        ).forEach { assertNull(it.toString(), data.withClosed(data.closed().copy(detail = it)).context()) }
    }

    @Test fun expenseRequiresAMatchingUndisclosedRealGameFact() {
        val data = reserveFixture()
        val entry = data.history.last()
        val fact = entry.facts.single()
        val detail = fact.detail as UnexpectedExpense
        val unusable = listOf(
            fact.copy(detail = detail.copy(previouslyDisclosed = true)),
            fact.copy(detail = detail.copy(amount = detail.amount + 1)),
            fact.copy(detail = detail.copy(operationId = "another-receipt")),
            fact.copy(mode = AnalyticsMode.DEMO),
            fact.copy(mode = AnalyticsMode.SIMULATION),
            fact.copy(learningContext = LearningContext.TRAINING),
            fact.copy(learningContext = LearningContext.COUNTERFACTUAL),
            fact.copy(context = fact.context.copy(day = 4)),
        )
        unusable.forEach { assertNull(it.toString(), data.withEntry(entry.copy(facts = listOf(it))).context()) }
        assertNull(data.withEntry(entry.copy(facts = emptyList())).context())
    }

    @Test fun aBareWalletExpenseDoesNotProveThatTheExpenseWasUnexpected() {
        val data = reserveFixture()
        val expense = data.history.last()
        val ordinary = expense.facts.single().copy(detail = Interaction("CompleteEvent"))
        assertNull(data.withEntry(expense.copy(facts = listOf(ordinary))).context())
    }

    @Test fun missingOrInconsistentLedgerReceiptsAreRejected() {
        val data = reserveFixture()
        val expense = data.history.last()
        val after = expense.after!!
        assertNull(data.withEntry(expense.copy(operations = emptyList())).context())
        assertNull(data.withEntry(expense.copy(operations = listOf(
            expense.operations.single().copy(amount = 3)))).context())
        assertNull(data.withEntry(expense.copy(operations = listOf(
            expense.operations.single().copy(kind = LedgerKind.INCOME)))).context())
        assertNull(data.withEntry(expense.copy(after = after.copy(
            economy = after.economy.copy(availableBalance = 69)))).context())
    }

    @Test fun repeatedOperationIdentityAcrossDifferentCommandsIsAmbiguous() {
        val first = expenseEntry()
        val second = expenseEntry(id = "another-command", sequence = 3, day = 4)
        assertNull(reserveFixture(expenses = listOf(first, second)).context())
    }

    @Test fun receiptsFromAnotherRunCannotSupplyTheCurrentChildsContext() {
        val data = reserveFixture()
        val expense = data.history.last()
        val foreign = expense.copy(runId = "foreign-run", facts = expense.facts.map {
            it.copy(gameRunId = "foreign-run")
        })
        assertNull(data.withEntry(foreign).context())
    }

    @Test fun demoAndRestoredEntriesAreNeverUsedAsRealExpenseContexts() {
        val data = reserveFixture()
        val expense = data.history.last()
        assertNull(data.withEntry(expense.copy(request = expense.request!!.copy(demoMode = true))).context())
        assertNull(data.withEntry(expense.copy(type = AuditType.RESTORED)).context())
        assertNull(data.withEntry(expense.copy(type = AuditType.IMPORTED_BASELINE)).context())
        assertNull(data.withClosed(data.closed().copy(mode = AnalyticsMode.DEMO)).context())
        assertNull(data.withClosed(data.closed().copy(learningContext = LearningContext.TRAINING)).context())
    }

    @Test fun missingJournalAndUnknownJournalSourceCannotBeRepairedFromOpaqueIds() {
        val data = reserveFixture()
        val expense = data.history.last()
        val after = expense.after!!
        val day = after.engine!!
        assertNull(data.withEntry(expense.copy(after = after.copy(engine = day.copy(journal = emptyList())))).context())
        listOf(
            day.journal.single().copy(sourceId = "unknown-choice"),
            day.journal.single().copy(id = "another-receipt"),
            day.journal.single().copy(kind = DayJournalKind.MEAL),
        ).forEach { journal ->
            assertNull(journal.toString(), data.withEntry(expense.copy(after = after.copy(
                engine = day.copy(journal = listOf(journal))))).context())
        }
    }

    @Test fun aMissingOrBlankCatalogTitleIsNotReplacedByAnIdentifier() {
        val data = reserveFixture()
        assertNull(data.context(content.copy(events = emptyList())))
        assertNull(data.context(content.copy(events = listOf(event(eventId, "  ")))))
        assertNull(data.context(content.copy(choices = emptyList())))
    }

    @Test fun severalAttributedReceiptsMayShareOneUnambiguousEventContext() {
        val first = expenseEntry(operationId = "expense-a")
        val second = expenseEntry(id = "second-command", sequence = 3, operationId = "expense-b")
        val data = reserveFixture(listOf(first, second), listOf(
            ReserveApplication("expense-a", 2), ReserveApplication("expense-b", 2)))
        assertEquals(SkillEventContext(3, title), data.context())
    }

    @Test fun expensesOnDifferentDaysOrInDifferentEventsDoNotCollapseIntoOneExample() {
        val first = expenseEntry(operationId = "expense-a")
        val otherDay = expenseEntry(id = "second-command", sequence = 3, day = 4, operationId = "expense-b")
        val attributions = listOf(ReserveApplication("expense-a", 2), ReserveApplication("expense-b", 2))
        assertNull(reserveFixture(listOf(first, otherDay), attributions).context())

        val otherEvent = expenseEntry(id = "second-command", sequence = 3, operationId = "expense-b",
            journalKind = DayJournalKind.EVENT_START, event = "another-event")
        val bothEvents = content.copy(events = content.events + event("another-event", "Фонарь разбился"))
        assertNull(reserveFixture(listOf(first, otherEvent), attributions).context(bothEvents))
    }

    @Test fun anUnknownApplicationDoesNotAllowTheRemainingKnownOneToStandForTheWholeObservation() {
        val data = reserveFixture(attributions = listOf(
            ReserveApplication("receipt-opaque", 2), ReserveApplication("missing-expense", 2)))
        assertNull(data.context())
    }

    @Test fun directEventEvidenceUsesTheExplicitChoiceJournal() {
        val data = directFixture()
        assertEquals(SkillEventContext(3, title), data.context())
    }

    @Test fun directEventEvidenceCanUseTheCommandOccurrenceWhenNoJournalWasCreated() {
        val data = directFixture(withJournal = false)
        assertEquals(SkillEventContext(3, title), data.context())
    }

    @Test fun directContextAlsoUsesFactIdentityRatherThanTransportSequence() {
        val data = directFixture()
        assertEquals(SkillEventContext(3, title), data.copy(facts = data.facts.map {
            it.copy(sequence = 1000)
        }).context())
    }

    @Test fun directContextDoesNotGuessFromAnEpisodeOrActionThatLooksLikeAnEventId() {
        val data = directFixture(withJournal = false)
        val entry = data.history.single()
        val before = entry.before!!
        val after = entry.after!!
        val noOccurrence = entry.copy(request = entry.request!!.copy(command = EngineCommand.FinishDay),
            before = before.copy(story = before.story.copy(activeEventId = null),
                engine = before.engine!!.copy(events = emptyList())),
            after = after.copy(story = after.story.copy(activeEventId = null),
                engine = after.engine!!.copy(events = emptyList())))
        val changed = data.withEntry(noOccurrence)
        assertNull(changed.copy(observation = changed.observation.copy(episodeId = eventId),
            facts = changed.facts.map { it.copy(episodeId = eventId) }).context())
    }

    @Test fun directEvidenceCannotAttachAnotherRunsDemoOrRestoredCommand() {
        val data = directFixture()
        val entry = data.history.single()
        assertNull(data.withEntry(entry.copy(request = entry.request!!.copy(demoMode = true))).context())
        assertNull(data.withEntry(entry.copy(type = AuditType.RESTORED)).context())
        assertNull(data.copy(observation = data.observation.copy(gameRunId = "another-run")).context())
        assertNull(data.copy(facts = emptyList()).context())
        assertNull(data.copy(facts = data.facts.map { it.copy(episodeId = "another-episode") }).context())
    }

    private inner class Fixture(
        val history: List<AuditEntry>,
        val facts: List<AnalyticsFact>,
        val observation: SkillObservation,
    ) {
        fun copy(history: List<AuditEntry> = this.history, facts: List<AnalyticsFact> = this.facts,
            observation: SkillObservation = this.observation) = Fixture(history, facts, observation)

        fun context(catalog: StoryContent = content): SkillEventContext? =
            SkillExplanationContexts.from(history, facts, catalog).contextFor(observation)

        fun closed() = facts.single { it.eventId == "derived-reserve" }

        fun withClosed(fact: AnalyticsFact) = copy(facts = facts.map {
            if (it.eventId == "derived-reserve") fact else it
        })

        fun withEntry(entry: AuditEntry): Fixture {
            val previous = history.single { it.id == entry.id }
            val previousFactIds = previous.facts.map { it.eventId }.toSet()
            return copy(history = history.map { if (it.id == entry.id) entry else it },
                facts = facts.filterNot { it.eventId in previousFactIds } + entry.facts)
        }
    }

    private fun reserveFixture(
        expenses: List<AuditEntry> = listOf(expenseEntry()),
        attributions: List<ReserveApplication> = listOf(ReserveApplication("receipt-opaque", 4)),
    ): Fixture {
        val state = state(day = 1, available = 70)
        val planFact = AnalyticsFact("plan-fact", "run", "reserve-episode", "plan-command", 1,
            ReserveDecision("reserve-plan", 10, 10, 0, false), shown(1, 70, 70))
        val plan = AuditEntry("plan-entry", 1, "run", AuditType.COMMAND,
            EngineRequest("plan-command", 0, EngineCommand.ConfirmBudget("draft", 0)),
            before = state, after = state, facts = listOf(planFact))
        val used = attributions.sumOf { it.amount }
        val closed = planFact.copy(eventId = "derived-reserve", sequence = 9,
            detail = ReserveDecision("reserve-plan", 10, 10 - used, used, true, attributions),
            context = shown(1, 70, 70 - used))
        val history = listOf(plan) + expenses
        return Fixture(history, history.flatMap { it.facts } + closed,
            observation(SkillId.BUILD_EMERGENCY_FUND, "reserve-episode", ObservationReason.RESERVE_USED,
                listOf(planFact.eventId, closed.eventId)))
    }

    private fun directFixture(withJournal: Boolean = true): Fixture {
        val base = expenseEntry()
        val before = base.before!!
        val recordedAfter = base.after!!
        val after = recordedAfter.copy(economy = before.economy,
            engine = recordedAfter.engine!!.copy(journal = if (withJournal) listOf(
                DayJournalEntry("choice-journal", DayJournalKind.EVENT_CHOICE, choiceId, 0)) else emptyList()))
        val fact = base.facts.single().copy(eventId = "direct-fact", episodeId = "direct-episode",
            detail = OptionalPurchase("item-opaque", 4, false), context = shown(3, 70, 70), actor = AnalyticsActor.CHILD)
        val entry = base.copy(after = after, facts = listOf(fact), operations = emptyList())
        return Fixture(listOf(entry), listOf(fact), observation(SkillId.PRIORITIZE_NEEDS, "direct-episode",
            ObservationReason.NEEDS_COVERED, listOf(fact.eventId)))
    }

    private fun expenseEntry(id: String = "expense-command", sequence: Long = 2, day: Int = 3,
        operationId: String = "receipt-opaque", journalKind: DayJournalKind = DayJournalKind.EVENT_CHOICE,
        event: String = eventId): AuditEntry {
        val occurrence = EventOccurrence("occurrence-opaque", event, EventOrigin.SCHEDULE, EventStatus.ACTIVE)
        val opening = journalKind == DayJournalKind.EVENT_START
        val before = state(day, 70, if (opening) null else occurrence)
        val journal = DayJournalEntry(operationId, journalKind,
            if (journalKind == DayJournalKind.EVENT_START) event else choiceId, -4)
        val after = state(day, 66, if (opening) occurrence else occurrence.copy(status = EventStatus.COMPLETED), listOf(journal))
        val command = if (opening) EngineCommand.OpenNextEvent
            else EngineCommand.CompleteEvent(occurrence.id, choiceId)
        val fact = AnalyticsFact("$id-fact", "run", "expense-episode", id, sequence,
            UnexpectedExpense(operationId, 4, false), shown(day, 70, 66), actor = AnalyticsActor.SYSTEM)
        return AuditEntry(id, sequence, "run", AuditType.COMMAND, EngineRequest(id, 0, command),
            before = before, after = after, facts = listOf(fact),
            operations = listOf(LedgerEntry(operationId, LedgerKind.AVAILABLE_EXPENSE, 4)))
    }

    private fun state(day: Int, available: Long, occurrence: EventOccurrence? = null,
        journal: List<DayJournalEntry> = emptyList()) = GameState(
        PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(BudgetPlan(0, 0, 0, 0), availableBalance = available, savingsBalance = 10),
        StoryState(null, null, occurrence?.takeIf { it.status == EventStatus.ACTIVE }?.eventId, emptyList()),
        0, 0, emptyList(), EngineState("rules", 0, day, DayPhase.RUNNING, 0, 5, false, null, 70,
            listOfNotNull(occurrence), emptyList(), journal = journal),
    )

    private fun shown(day: Int, before: Long, after: Long) = DecisionContext("shown", true, true,
        FinancialPosition(before, 10, 20), FinancialPosition(after, 10, 20), true, day = day)

    private fun observation(skill: SkillId, episode: String, reason: ObservationReason, sources: List<String>) =
        SkillObservation("run", skill, episode, ObservationOutcome.SUPPORTED, ObservationEligibility.ELIGIBLE,
            EpisodeCompletion.COMPLETE, reason, sources, emptySet(), false, setOf(LearningContext.GAME), setOf("test"))

    private fun event(id: String, title: String) = EventDefinition(id, EventType.RANDOM, title,
        "Неожиданно понадобилась помощь.", null, null, null, 0, null, null)
}
