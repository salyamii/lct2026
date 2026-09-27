package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.withStarterAccessoryOwnership
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage
import ru.nksk.lctapp.domain.economy.EconomyOperations

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
    val presentation: EventPresentation = EventPresentation(),
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
    /** Direct old -> current IDs for compatible, unresolved scheduled occurrences only. */
    val eventReplacements: Map<String, String> = emptyMap(),
) {
    // A derived helper has no backing field, so it does not become authored fingerprint data.
    val mealPolicy: MealPolicy get() = MealPolicy(meals)

    fun storyProgress(state: GameState) = StoryProgress(content, policies, goals, storyCampaign, state)

    // Existing saves keep their current day reference; only a finale changes its chapter.
    fun dayId(state: GameState): String = state.story.currentDayId ?: storyDayId

    /** Stable rotation of eligible content; actual exposure is recorded only when a card is shown. */
    fun plan(state: GameState): List<String> = EventScheduler.plan(this, state)
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
    private val eventReplacements = EventOccurrenceReplacements(catalog)
    private val mutableAppliedCommands = MutableSharedFlow<AppliedGameCommand>(
        replay = 0, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Only newly confirmed commands. No history replay, durable delivery, or lifecycle catch-up. */
    val appliedCommands = mutableAppliedCommands.asSharedFlow()
    val contentFingerprint = ru.nksk.lctapp.domain.timemachine.GameCatalogFingerprint.compute(catalog)
    val engine = GameEngine(games, EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign), catalog.rules,
        contentFingerprint, onApplied = { mutableAppliedCommands.tryEmit(it) })
    val timeMachine = ru.nksk.lctapp.domain.timemachine.TimeMachine(games, engine, catalog, contentFingerprint)

    fun observeHistory() = games.observeHistory()
    suspend fun history() = games.readHistory()
    suspend fun exportSnapshot() = games.exportSnapshot()
    suspend fun restoreSnapshot(snapshot: ru.nksk.lctapp.domain.history.GameSnapshot,
        guard: ru.nksk.lctapp.domain.history.RestoreGuard): GameState {
        content.install(catalog.content)
        games.restoreSnapshot(snapshot, guard)
        eventReplacements.synchronize(games)
        games.synchronizeStarterAccessory()
        engine.synchronizeStoryAge()
        return checkNotNull(games.read())
    }
    suspend fun recordFacts(facts: List<ru.nksk.lctapp.domain.analytics.AnalyticsFact>) = games.recordFacts(facts)

    suspend fun skillProfiles(): List<ru.nksk.lctapp.domain.analytics.SkillProfile> {
        val history = games.readHistory()
        val runId = history.lastOrNull()?.runId ?: return emptyList()
        return withContext(Dispatchers.Default) {
            ru.nksk.lctapp.domain.analytics.SkillEvaluator().project(runId,
                ru.nksk.lctapp.domain.history.HistoryLearningProjection.facts(history, catalog.content))
        }
    }

    /** Available projects for a new game, before there is a persisted aggregate. */
    val onboardingGoals: List<GoalCampaign> get() = catalog.goals.filter { catalog.storyProgress(initial).goalAvailable(it) }
    val onboardingSavingItemIds: List<String> get() = onboardingGoals.flatMap { it.itemIds }

    suspend fun prepare(
        pet: PetState? = null,
        goalId: String? = null,
        savingItemId: String? = null,
        beginInitialAllocation: Boolean = false,
    ) = preparation.withLock {
        if (!prepared) {
            content.install(catalog.content)
            require(goalId == null || onboardingGoals.any { it.goalId == goalId }) { "Unavailable starting goal" }
            require(savingItemId == null || savingItemId in onboardingSavingItemIds) { "Unavailable starting saving target" }
            val startingGoal = savingItemId?.let { item -> onboardingGoals.first { item in it.itemIds }.goalId }
                ?: goalId ?: initial.selectedGoalId
            // The onboarding explanation already introduced these coins. Create the
            // new save at allocation, atomically with its chosen target and accessory.
            // initializeIfAbsent still preserves any save committed in the meantime.
            val planning = initial.economy.planning
            val economy = if (beginInitialAllocation && planning?.reason == BudgetPlanningReason.INITIAL &&
                planning.stage == BudgetPlanningStage.RECEIPT) {
                EconomyOperations.startAllocation(initial.economy, planning.id, planning.revision)
            } else initial.economy
            if (games.read() == null) {
                games.initializeIfAbsent(initial.copy(pet = pet ?: initial.pet, economy = economy,
                    selectedGoalId = startingGoal, selectedSavingItemId = savingItemId ?: initial.selectedSavingItemId)
                    .withStarterAccessoryOwnership())
            }
            eventReplacements.synchronize(games)
            games.synchronizeStarterAccessory()
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

    fun selectSavingGoalCommand(state: GameState, goalId: String, itemId: String) = EngineCommand.SelectSavingGoal(goalId, itemId,
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
