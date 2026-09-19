package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

/** Authored copy and artwork keys stay open data, independently of Android resources. */
data class EventCardCopy(
    val category: String,
    val impact: String,
    val effort: String,
    val later: String?,
    val footer: String,
    val sourceUrl: String,
    val scene: String,
    val character: String?,
)

data class GameCatalog(
    val content: StoryContent,
    val policies: Map<String, EventPolicy>,
    val cards: Map<String, EventCardCopy>,
    val rules: EngineRules,
    val meals: List<MealDefinition>,
    val storyDayId: String,
    val introductionId: String,
    val deedPool: List<String>,
    val dailyEventPool: List<String> = emptyList(),
    val oneTimeEventIds: Set<String> = emptySet(),
) {
    /** Temporary deterministic content rotation; the selected plan is persisted by BeginDay. */
    fun plan(state: GameState): List<String> {
        val carried = state.engine?.events.orEmpty().filter {
            it.status == EventStatus.CARRIED || it.status == EventStatus.CARRIED_ACTIVE
        }.map { it.eventId }
        val introductionCompleted = state.story.decisions.any { decision ->
            content.choices.any { it.id == decision.choiceId && it.eventId == introductionId }
        }
        val remaining = carried + if (introductionCompleted || introductionId in carried || carried.size >= 5)
            emptyList() else listOf(introductionId)
        require(remaining.size <= 5 && deedPool.isNotEmpty())
        val offset = (state.engine?.day ?: 0) * 3L
        val completed = state.story.decisions.map { it.choiceId }.toSet()
        val everyday = dailyEventPool.filter { id ->
            id !in remaining && (id !in oneTimeEventIds ||
                content.choices.none { it.eventId == id && it.id in completed })
        }
        val extras = if (everyday.isEmpty()) emptyList() else List(minOf(2, everyday.size)) {
            everyday[((state.engine?.day?.toLong() ?: 0L) + it).rem(everyday.size).toInt()]
        }
        val slots = (4 - remaining.size).coerceAtLeast(0)
        // Keep an earning opportunity before expenses; carried entries always keep their prefix.
        val fill = mutableListOf<String>()
        if (slots > 0) fill += deedPool[(offset % deedPool.size).toInt()]
        fill += extras.take((slots - fill.size).coerceAtLeast(0))
        while (fill.size < slots) fill += deedPool[((offset + fill.size) % deedPool.size).toInt()]
        return remaining + fill
    }
}

/** Installs immutable content once, then delegates every game write to the aggregate engine. */
class GameSession(
    private val games: GameRepository,
    private val content: StoryContentRepository,
    val catalog: GameCatalog,
    private val initial: GameState,
) {
    private val preparation = Mutex()
    private var prepared = false
    val engine = GameEngine(games, EventFactory(catalog.content, catalog.policies, catalog.meals), catalog.rules)

    suspend fun prepare() = preparation.withLock {
        if (!prepared) {
            content.install(catalog.content)
            games.initializeIfAbsent(initial)
            prepared = true
        }
    }

    /** Observe committed snapshots. Call prepare once before observing a potentially new save. */
    fun observe() = games.observe()

    /** Read one committed snapshot from storage, without advancing or initializing the game. */
    suspend fun read(): GameState? = games.read()

    suspend fun dispatch(request: EngineRequest): EngineResult {
        prepare()
        return engine.dispatch(request)
    }

    fun advanceCommand(state: GameState): EngineCommand? = when {
        state.engine == null || state.engine.phase == DayPhase.FINISHED ->
            // The first Continue starts a new save; waking after a summary only prepares the day.
            EngineCommand.BeginDay(catalog.storyDayId, catalog.plan(state), openFirst = state.engine == null)
        state.engine.currentEvent != null -> null
        state.engine.energy == 0 -> EngineCommand.FinishDay
        state.engine.phase == DayPhase.READY_TO_END -> EngineCommand.FinishDay
        engine.blockReason(state, EngineCommand.OpenNextEvent) == BlockReason.MustSleep -> EngineCommand.FinishDay
        else -> EngineCommand.OpenNextEvent
    }
}
