package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

class EventOccurrenceReplacementsTest {
    private val oldId = "repair-v2"
    private val newId = "repair-v3"
    private val rules = EngineRules("replacement-test", 5, 3, 1)
    private val catalog = GameCatalog(
        content = StoryContent(
            events = listOf(oldId, newId).map {
                EventDefinition(it, EventType.RANDOM, it, "Repair", null, null, null, 0, null, null)
            },
            choices = listOf(oldId, newId).map {
                EventChoiceDefinition("$it:pay", it, 0, "Pay", -3, null, null, GoalImpact.NEUTRAL)
            } + EventChoiceDefinition("$newId:work", newId, 1, "Clean", 0, null, null, GoalImpact.NEUTRAL),
        ),
        policies = listOf(oldId, newId).associateWith { EventPolicy(0) },
        cards = emptyMap(), rules = rules, meals = emptyList(), storyDayId = "day",
        introductionId = oldId, deedPool = emptyList(), eventReplacements = mapOf(oldId to newId),
    )

    private fun state(status: EventStatus = EventStatus.ACTIVE): GameState {
        val occurrence = EventOccurrence("same-occurrence", oldId, EventOrigin.SCHEDULE, status)
        val engine = EngineState(rules.id, 7, 5, DayPhase.RUNNING, 2, 3, true, null, 40,
            listOf(occurrence), emptyList(), openingEnergy = 5,
            journal = listOf(DayJournalEntry("earlier-expense", DayJournalKind.EVENT_CHOICE, "$oldId:pay", -3)))
        return GameState(PetState("PLAIN", PetVisualState.THINKING), EconomyState(BudgetPlan(15, 10, 0, 12)),
            StoryState("day", null, engine.currentEvent?.eventId,
                listOf(StoryDecision("previous-occurrence:decision", "$oldId:pay"))),
            11, 12, listOf(OwnedItem("owned-first", "item"), OwnedItem("owned-repeat", "item")),
            engine = engine, eventHistory = listOf(EventExposure(oldId, 5, 2, 3, 1)))
    }

    @Test fun unresolvedStatusesKeepIdentityOrderingAndAllActualConsequences() {
        val replacements = EventOccurrenceReplacements(catalog)
        for (status in listOf(EventStatus.PENDING, EventStatus.ACTIVE, EventStatus.PAUSED,
            EventStatus.CARRIED, EventStatus.CARRIED_ACTIVE)) {
            val before = state(status)
            val oldDay = before.engine!!
            val after = replacements.apply(before)
            val expectedDay = oldDay.copy(revision = 8,
                events = oldDay.events.map { it.copy(eventId = newId) })
            assertEquals(before.copy(engine = expectedDay,
                story = before.story.copy(activeEventId = expectedDay.currentEvent?.eventId)), after)
            assertSame(after, replacements.apply(after))
        }
    }

    @Test fun resultsCompletedOccurrencesAndRecordedDecisionsAreNeverRewritten() {
        val replacements = EventOccurrenceReplacements(catalog)
        for (status in listOf(EventStatus.RESULT, EventStatus.COMPLETED)) {
            val before = state(status)
            assertSame(before, replacements.apply(before))
        }
        val before = state().let { it.copy(story = it.story.copy(decisions = it.story.decisions +
            StoryDecision("same-occurrence:decision", "$oldId:pay"))) }
        assertSame(before, replacements.apply(before))
    }

    @Test fun mixedOccurrencesKeepOrderAndTheResolvedCopyOfTheSameDefinition() {
        val before = state().let { current -> current.copy(engine = current.engine!!.copy(events = listOf(
            EventOccurrence("old-completed", oldId, EventOrigin.SCHEDULE, EventStatus.COMPLETED),
            current.engine.events.single(),
            EventOccurrence("later-paused", oldId, EventOrigin.SCHEDULE, EventStatus.PAUSED),
        ))) }
        val after = EventOccurrenceReplacements(catalog).apply(before)
        assertEquals(before.engine!!.events.map { it.id }, after.engine!!.events.map { it.id })
        assertEquals(listOf(oldId, newId, newId), after.engine.events.map { it.eventId })
        assertEquals(before.story.decisions, after.story.decisions)
        assertEquals(before.eventHistory, after.eventHistory)
        assertEquals(before.engine.journal, after.engine.journal)
    }

    @Test fun deedOccurrencesAreLeftWithTheirOriginalOffer() {
        val before = state().let { current -> current.copy(engine = current.engine!!.copy(
            events = listOf(current.engine.events.single().copy(origin = EventOrigin.DEED, deedOfferId = "offer")),
            deeds = listOf(DeedOffer("offer", oldId, 7)),
        )) }
        assertSame(before, EventOccurrenceReplacements(catalog).apply(before))
    }

    @Test fun synchronizationTransformsLatestTransactionalAggregateAndOnlyAppendsTechnicalHistory() = runTest {
        val repo = MemoryRepository(state())
        val oldHistory = repo.readHistory()
        val latest = state().let { it.copy(engine = it.engine!!.copy(revision = 19, energy = 1, steps = 4)) }
        repo.beforeUpdate = { repo.recordConcurrentChange(latest) }
        EventOccurrenceReplacements(catalog).synchronize(repo)
        assertEquals(20L, repo.read().engine!!.revision)
        assertEquals(1, repo.read().engine!!.energy)
        assertEquals(4, repo.read().engine!!.steps)
        assertEquals(oldHistory, repo.readHistory().take(oldHistory.size))
        val technical = repo.readHistory().last()
        assertEquals(AuditType.TECHNICAL_UPDATE, technical.type)
        assertEquals(latest, technical.before)
        assertTrue(technical.operations.isEmpty() && technical.facts.isEmpty())
        val count = repo.readHistory().size
        EventOccurrenceReplacements(catalog).synchronize(repo)
        assertEquals(count, repo.readHistory().size)
    }

    @Test fun prepareAndExplicitRestoreBothAdoptRevisionWithoutEditingSnapshotHistory() = runTest {
        val original = state()
        val repo = MemoryRepository(original)
        val snapshot = HistoryCodec.snapshot("run", original, repo.readHistory())
        val content = object : StoryContentRepository {
            var installed: StoryContent? = null
            override suspend fun read() = checkNotNull(installed)
            override suspend fun install(content: StoryContent) { installed = content }
        }
        val session = GameSession(repo, content, catalog, original)
        session.prepare()
        assertEquals(newId, session.read()!!.engine!!.currentEvent!!.eventId)
        val count = repo.readHistory().size
        session.prepare()
        assertEquals(count, repo.readHistory().size)
        assertEquals(catalog.content, content.read())
        assertEquals(newId, session.restoreSnapshot(snapshot, RestoreGuard(8, count.toLong())).engine!!.currentEvent!!.eventId)
        assertEquals(snapshot.history, repo.readHistory().take(snapshot.history.size))
        assertEquals(oldId, snapshot.state.engine!!.currentEvent!!.eventId)
        assertEquals(8L, repo.read().engine!!.revision)
    }

    @Test fun invalidReplacementGraphsAndChangedEntryEffectsAreRejected() {
        for (map in listOf(mapOf(oldId to oldId), mapOf(oldId to newId, newId to oldId),
            mapOf(oldId to "unknown"))) {
            assertThrows(IllegalArgumentException::class.java) { EventOccurrenceReplacements(catalog.copy(eventReplacements = map)) }
        }
        val changed = catalog.content.copy(events = catalog.content.events.map {
            if (it.id == newId) it.copy(moneyDeltaOnStart = -1) else it
        })
        assertThrows(IllegalArgumentException::class.java) { EventOccurrenceReplacements(catalog.copy(content = changed)) }
        assertThrows(IllegalArgumentException::class.java) {
            EventOccurrenceReplacements(catalog.copy(policies = catalog.policies +
                (newId to EventPolicy(0, startEffectsTiming = EffectTiming.OPEN))))
        }
    }

    private class MemoryRepository(initial: GameState) : GameRepository {
        val flow = MutableStateFlow(initial)
        var beforeUpdate: (() -> Unit)? = null
        private val history = mutableListOf(AuditEntry("initial", 1, "run", AuditType.INITIALIZED, after = initial))
        override fun observe() = flow
        override suspend fun read() = flow.value
        override suspend fun initializeIfAbsent(initial: GameState) = flow.value
        override suspend fun readHistory() = history.toList()
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            beforeUpdate?.also { beforeUpdate = null }?.invoke()
            val after = transform(flow.value)
            recordConcurrentChange(after)
            return after
        }
        fun recordConcurrentChange(after: GameState) {
            val before = flow.value
            if (before != after) {
                flow.value = after
                val sequence = history.size.toLong() + 1
                history += AuditEntry("technical:$sequence", sequence, "run", AuditType.TECHNICAL_UPDATE,
                    before = before, after = after)
            }
        }
        override suspend fun restoreSnapshot(snapshot: GameSnapshot, expected: RestoreGuard): GameState {
            HistoryCodec.validate(snapshot)
            flow.value = snapshot.state
            history.clear()
            history += snapshot.history
            val sequence = history.size.toLong() + 1
            history += AuditEntry("restored:$sequence", sequence, "run", AuditType.RESTORED, after = snapshot.state)
            return snapshot.state
        }
    }
}
