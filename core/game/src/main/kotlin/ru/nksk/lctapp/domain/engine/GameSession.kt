package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState

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
    val variants: List<EventCardVariant> = emptyList(),
    /** Short past-tense outcomes keyed by the choice actually made, for the day recap. */
    val summaryByChoiceId: Map<String, String> = emptyMap(),
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
    val goals: List<GoalCampaign> = emptyList(),
    val storyCampaign: StoryCampaign? = null,
) {
    fun storyProgress(state: GameState) = StoryProgress(content, policies, goals, storyCampaign, state)

    // Existing saves keep their current day reference; only a finale changes its chapter.
    fun dayId(state: GameState): String = state.story.currentDayId ?: storyDayId

    /** Temporary deterministic content rotation; the selected plan is persisted by BeginDay. */
    fun plan(state: GameState): List<String> {
        val carried = state.engine?.events.orEmpty().filter {
            it.status == EventStatus.CARRIED || it.status == EventStatus.CARRIED_ACTIVE
        }.map { it.eventId }.take(5)
        val introductionCompleted = state.story.decisions.any { decision ->
            content.choices.any { it.id == decision.choiceId && it.eventId == introductionId }
        }
        val storyEvent = if (storyCampaign != null) storyProgress(state).nextEvent(carried.toSet())
            else introductionId.takeUnless { (goals.isNotEmpty() && goals.selectedGoal(state) == null) || introductionCompleted || it in carried }
        val remaining = carried + if (carried.size < 5 && storyEvent != null) listOf(storyEvent) else emptyList()
        require(remaining.size <= 5 && deedPool.isNotEmpty())
        val offset = (state.engine?.day ?: 0) * 3L
        val completed = state.story.decisions.map { it.choiceId }.toSet()
        val everyday = dailyEventPool.filter { id ->
            id !in remaining && storyProgress(state).eligible(id) && (id !in oneTimeEventIds ||
                content.choices.none { it.eventId == id && it.id in completed })
        }
        val extras = if (everyday.isEmpty()) emptyList() else List(minOf(2, everyday.size)) {
            everyday[((state.engine?.day?.toLong() ?: 0L) + it).rem(everyday.size).toInt()]
        }
        val slots = (4 - remaining.size).coerceAtLeast(0)
        // Keep an earning opportunity before expenses; carried entries always keep their prefix.
        val fill = mutableListOf<String>()
        val helpfulDeed = storyCampaign?.deedHints?.firstOrNull {
            goals.selectedGoal(state) != null && storyProgress(state).meets(it.condition)
        }?.eventId
        if (slots > 0) fill += helpfulDeed ?: deedPool[(offset % deedPool.size).toInt()]
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
    val engine = GameEngine(games, EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign), catalog.rules)

    /** Available projects for a new game, before there is a persisted aggregate. */
    val onboardingGoals: List<GoalCampaign> get() = catalog.goals.filter { it.isAvailable(initial) }

    suspend fun prepare(pet: PetState? = null, goalId: String? = null) = preparation.withLock {
        if (!prepared) {
            content.install(catalog.content)
            require(goalId == null || onboardingGoals.any { it.goalId == goalId }) { "Unavailable starting goal" }
            games.initializeIfAbsent(initial.copy(pet = pet ?: initial.pet, selectedGoalId = goalId ?: initial.selectedGoalId))
            engine.synchronizeStoryAge()
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

    fun selectGoalCommand(state: GameState, goalId: String) = EngineCommand.SelectGoal(goalId,
        if (state.engine == null) EngineCommand.BeginDay(catalog.dayId(state), catalog.plan(state)) else null)

    private fun awaitsIntroduction(state: GameState): Boolean = catalog.goals.selectedGoal(state)?.let { goal ->
        state.story.decisions.none { decision -> catalog.content.choices.any {
            it.id == decision.choiceId && it.eventId == goal.introductionEventId
        } }
    } == true

    fun previewAdvanceSpending(state: GameState): EventSpendingPreview? = engine.advanceSpending(state, advanceCommand(state))

    fun advanceCommand(state: GameState): EngineCommand? = when {
        state.economy.planning != null || state.economy.unallocated != 0L -> null
        state.engine == null || state.engine.phase == DayPhase.FINISHED ->
            // The first Continue starts a new save; waking after a summary only prepares the day.
            EngineCommand.BeginDay(catalog.dayId(state), catalog.plan(state), openFirst = state.engine == null)
        state.engine.currentEvent != null -> null
        state.engine.energy == 0 -> EngineCommand.FinishDay
        state.engine.phase == DayPhase.READY_TO_END -> EngineCommand.FinishDay
        catalog.storyCampaign == null && awaitsIntroduction(state) -> EngineCommand.OpenNextEvent
        engine.blockReason(state, EngineCommand.OpenNextEvent) in setOf(BlockReason.MustSleep, BlockReason.NoNextEvent) -> EngineCommand.FinishDay
        else -> EngineCommand.OpenNextEvent
    }
}
