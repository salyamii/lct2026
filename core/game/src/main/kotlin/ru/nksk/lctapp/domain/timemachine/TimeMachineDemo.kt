package ru.nksk.lctapp.domain.timemachine

import kotlinx.coroutines.flow.flowOf
import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.EventFactory
import ru.nksk.lctapp.domain.engine.EventOccurrence
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GameEngine
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType
import ru.nksk.lctapp.domain.history.CanonicalLedger
import ru.nksk.lctapp.domain.history.HistorySourceGuard
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.domain.story.StoryState

/**
 * A labelled jury example, not a past action of the player. Only immutable authored
 * content enters this sandbox: it cannot read or write the real repository/session.
 * Ordinary prices and the existing replay/quiz engine are used without demo cheats.
 */
class TimeMachineDemo private constructor(
    val machine: TimeMachine,
    private val memory: DemoHistory,
) {
    val game: GameState get() = memory.game
    val history: List<AuditEntry> get() = memory.entries

    companion object {
        suspend fun create(catalog: GameCatalog, day: Int): TimeMachineDemo? {
            require(day > 0)
            val fingerprint = GameCatalogFingerprint.compute(catalog)
            val factory = EventFactory(catalog.content, catalog.policies, catalog.meals,
                catalog.goals, catalog.storyCampaign)
            // Prefer currently scheduled offers; never invent an item, price or alternative.
            val offers = catalog.content.events.filter {
                it.id in catalog.dailyEventPool && it.type == EventType.WANT && it.moneyDeltaOnStart == 0L
            }
            for (event in offers) {
                val policy = catalog.policies[event.id]
                val choices = catalog.content.choices.filter { it.eventId == event.id &&
                    it.id !in policy?.disabledChoiceIds.orEmpty() &&
                    it.id !in policy?.feedsPetChoiceIds.orEmpty() &&
                    it.id !in policy?.choiceGameKinds.orEmpty() }
                val purchase = choices.firstOrNull { choice -> choice.moneyDelta < 0 &&
                    catalog.content.choiceItemEffects.any { it.choiceId == choice.id && it.operation == ItemOperation.ADD } }
                    ?: continue
                if (choices.none { it.moneyDelta == 0L }) continue
                val price = Math.negateExact(purchase.moneyDelta)
                val available = maxOf(100L, Math.addExact(35L, price))
                val occurrence = EventOccurrence("demo:offer", event.id, EventOrigin.SCHEDULE, EventStatus.ACTIVE)
                val before = GameState(
                    pet = PetState("plain", PetVisualState.NORMAL),
                    economy = EconomyState(BudgetPlan(needs = 35, wants = available - 35, savings = 0, reserve = 0)),
                    story = StoryState(catalog.storyDayId, null, event.id, emptyList()),
                    satiety = 0, fatigue = 0, ownedItems = emptyList(),
                    engine = EngineState(catalog.rules.id, 1, day, DayPhase.RUNNING, 0,
                        catalog.rules.fullEnergy, true, null, available, listOf(occurrence), emptyList()),
                )
                val memory = DemoHistory(before)
                val engine = GameEngine(memory, factory, catalog.rules, fingerprint)
                val request = EngineRequest("demo:purchase", before.engine?.revision,
                    EngineCommand.CompleteEvent(occurrence.id, purchase.id), demoMode = false)
                val after = runCatching { engine.transition(before, request) }.getOrNull() ?: continue
                memory.install(after, AuditEntry("demo:purchase", 1, DEMO_RUN, AuditType.COMMAND,
                    request = request, before = before, after = after,
                    operations = CanonicalLedger.fromTransition(before, after, request), contentFingerprint = fingerprint))
                val machine = TimeMachine(memory, engine, catalog, fingerprint)
                if (machine.availableReflections().decisions.isNotEmpty()) return TimeMachineDemo(machine, memory)
            }
            return null
        }

        private const val DEMO_RUN = "isolated-time-machine-demo"
    }

    /** Quiz attempts exist only for the lifetime of this example and never enter the sync outbox. */
    private class DemoHistory(initial: GameState) : GameRepository {
        var game: GameState = initial
            private set
        var entries: List<AuditEntry> = emptyList()
            private set

        fun install(after: GameState, entry: AuditEntry) {
            check(entries.isEmpty())
            game = after
            entries = listOf(entry)
        }

        override fun observe() = flowOf(game)
        override suspend fun read() = game
        override suspend fun readHistory() = entries
        override suspend fun initializeIfAbsent(initial: GameState): GameState = error("Demo world is read-only")
        override suspend fun update(transform: (GameState) -> GameState): GameState = error("Demo world is read-only")
        override suspend fun recordFacts(facts: List<AnalyticsFact>, sourceGuard: HistorySourceGuard?) {
            sourceGuard?.requireMatches(entries)
            require(facts.all { it.gameRunId == DEMO_RUN })
            val existing = entries.flatMap { it.facts }.associateBy { it.eventId }
            val fresh = facts.filter { fact ->
                existing[fact.eventId]?.let { previous -> require(previous == fact.copy(sequence = previous.sequence)) }
                fact.eventId !in existing
            }
            if (fresh.isNotEmpty()) {
                val sequence = entries.last().sequence + 1
                entries = entries + AuditEntry("demo:facts:$sequence", sequence, DEMO_RUN,
                    AuditType.FACTS, facts = fresh.map { it.copy(sequence = sequence) })
            }
        }
    }
}
