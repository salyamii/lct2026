package ru.nksk.lctapp.domain.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import ru.nksk.lctapp.domain.demo.DemoPreferencesRepository
import ru.nksk.lctapp.domain.demo.DisabledDemoPreferencesRepository
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

/** An explicit continuation resumes the current day or its summary, never skips planning. */
sealed interface ContinueDayPlan {
    data object NeedsBudget : ContinueDayPlan
    data class Day(val command: EngineCommand?) : ContinueDayPlan
}

/** Installs immutable content once, then delegates every game write to the aggregate engine. */
class GameSession(
    private val games: GameRepository,
    private val content: StoryContentRepository,
    val catalog: GameCatalog,
    private val initial: GameState,
    private val demoPreferences: DemoPreferencesRepository = DisabledDemoPreferencesRepository,
) {
    private val preparation = Mutex()
    private var prepared = false
    private val demoRequestLock = Mutex()
    private val pendingDemoRequests = mutableMapOf<String, EngineRequest>()
    @Volatile var demoModeEnabled: Boolean = false
        private set
    private data class AdvanceProjection(val state: GameState, val demoMode: Boolean, val command: EngineCommand?)
    @Volatile private var advanceProjection: AdvanceProjection? = null
    private val eventReplacements = EventOccurrenceReplacements(catalog)
    private val mutableAppliedCommands = MutableSharedFlow<AppliedGameCommand>(
        replay = 0, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Only newly confirmed commands. No history replay, durable delivery, or lifecycle catch-up. */
    val appliedCommands = mutableAppliedCommands.asSharedFlow()
    val contentFingerprint = ru.nksk.lctapp.domain.timemachine.GameCatalogFingerprint.compute(catalog)
    val engine = GameEngine(games, EventFactory(catalog.content, catalog.policies, catalog.meals, catalog.goals, catalog.storyCampaign), catalog.rules,
        contentFingerprint, onApplied = { mutableAppliedCommands.tryEmit(it) }, previewDemoMode = { demoModeEnabled })
    val timeMachine = ru.nksk.lctapp.domain.timemachine.TimeMachine(games, engine, catalog, contentFingerprint)

    fun observeHistory() = games.observeHistory()
    fun observeHistorySequence() = games.observeHistorySequence()
    suspend fun history() = games.readHistory()
    suspend fun commandReceipt(requestId: String) = games.readCommandReceipt(requestId)
    suspend fun latestCommand() = games.readLatestCommand()
    suspend fun expenseRecoveryHistory() = games.readExpenseRecoveryHistory()
    suspend fun budgetPlanHistory(planId: String) = games.readBudgetPlanHistory(planId)
    suspend fun recordedFacts(eventIds: Set<String>) = games.readFacts(eventIds)
    suspend fun exportSnapshot() = games.exportSnapshot()
    suspend fun snapshotHead() = games.readSnapshotHead()
    suspend fun archivedRuns() = games.archivedRuns()
    suspend fun archivedRun(runId: String) = games.archivedRun(runId)
    fun canRestartCampaign(state: GameState): Boolean = catalog.storyProgress(state).campaignComplete

    /** Archive first; a new profile is committed only after the player finishes onboarding. */
    suspend fun prepareCampaignRestart(request: ru.nksk.lctapp.domain.history.CampaignRestartRequest) = preparation.withLock {
        games.prepareCampaignRestart(request) { current ->
            check(canRestartCampaign(current)) { "The campaign is not complete" }
        }
        prepared = false
        advanceProjection = null
    }

    /** The final chronoscope starts the same authored journey, retaining identity but no gameplay gains. */
    suspend fun restartCampaign(request: ru.nksk.lctapp.domain.history.CampaignRestartRequest): GameState {
        content.install(catalog.content)
        return games.restartCampaign(request) { current, original ->
            check(canRestartCampaign(current)) { "The campaign is not complete" }
            val firstGoal = original?.selectedGoalId?.takeIf { id -> onboardingGoals.any { it.goalId == id } }
                ?: onboardingGoals.firstOrNull()?.goalId ?: initial.selectedGoalId
            val firstItem = original?.selectedSavingItemId?.takeIf { it in onboardingSavingItemIds }
                ?: initial.selectedSavingItemId
            initial.copy(
                pet = initial.pet.copy(name = current.pet.name, color = current.pet.color,
                    temperament = current.pet.temperament,
                    selectedLookId = original?.pet?.selectedLookId ?: initial.pet.selectedLookId),
                selectedGoalId = firstGoal,
                selectedSavingItemId = firstItem,
            ).withStarterAccessoryOwnership()
        }
    }
    suspend fun restoreSnapshot(snapshot: ru.nksk.lctapp.domain.history.GameSnapshot,
        guard: ru.nksk.lctapp.domain.history.RestoreGuard): GameState {
        content.install(catalog.content)
        games.restoreSnapshot(snapshot, guard)
        eventReplacements.synchronize(games)
        games.synchronizeStarterAccessory()
        engine.synchronizeStoryAge()
        return checkNotNull(games.read())
    }
    /** False means demo evidence was intentionally discarded; callers must not retry it. */
    suspend fun recordFacts(facts: List<ru.nksk.lctapp.domain.analytics.AnalyticsFact>): Boolean {
        if (demoPreferences.read().demoModeEnabled) return false
        games.recordFacts(facts)
        return true
    }

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
            demoModeEnabled = demoPreferences.read().demoModeEnabled
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
                // A retired screen or background caller cannot create the default hero while
                // a rewind waits for character selection, including after process recreation.
                check(pet != null || games.archivedRuns().isEmpty()) { "Finish character setup before starting the new campaign" }
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
    fun observe() = combine(games.observe(), demoPreferences.observe()) { state, preferences ->
        demoModeEnabled = preferences.demoModeEnabled
        state
    }

    /** Read one committed snapshot from storage, without advancing or initializing the game. */
    suspend fun read(): GameState? = games.read()

    suspend fun dispatch(request: EngineRequest): EngineResult {
        prepare()
        val captured = demoRequestLock.withLock {
            pendingDemoRequests[request.id]?.also { original ->
                require(original.copy(demoMode = request.demoMode) == request) { "Conflicting gameplay action identity" }
            } ?: run {
                // Never evict an uncertain write: its retry must keep the same rules.
                check(pendingDemoRequests.size < 64) { "Too many unresolved gameplay actions" }
                val enabled = demoPreferences.read().demoModeEnabled
                demoModeEnabled = enabled
                // A price already shown as free may never become a paid purchase on submit.
                if (request.demoMode && !enabled) return EngineResult.Blocked(BlockReason.StaleRevision)
                request.copy(demoMode = enabled).also { pendingDemoRequests[request.id] = it }
            }
        }
        val result = engine.dispatch(captured)
        demoRequestLock.withLock { pendingDemoRequests.remove(request.id) }
        return result
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

    fun continueDayPlan(state: GameState, demoMode: Boolean = demoModeEnabled): ContinueDayPlan = when {
        state.economy.planning != null || state.economy.unallocated != 0L -> ContinueDayPlan.NeedsBudget
        // Only the separate wake-up action on the summary may begin another day.
        state.engine?.phase == DayPhase.FINISHED -> ContinueDayPlan.Day(null)
        else -> ContinueDayPlan.Day(advanceCommand(state, demoMode))
    }

    fun advanceCommand(state: GameState, demoMode: Boolean = demoModeEnabled): EngineCommand? {
        advanceProjection?.takeIf { it.state === state && it.demoMode == demoMode }?.let { return it.command }
        // Immutable snapshots can share their pure admission result. The real commit always rechecks.
        val command = computeAdvanceCommand(state, demoMode)
        advanceProjection = AdvanceProjection(state, demoMode, command)
        return command
    }

    private fun computeAdvanceCommand(state: GameState, demoMode: Boolean): EngineCommand? = when {
        state.economy.planning != null || state.economy.unallocated != 0L -> null
        state.engine == null || state.engine.phase == DayPhase.FINISHED ->
            // The first Continue starts a new save; waking after a summary only prepares the day.
            EngineCommand.BeginDay(catalog.dayId(state), catalog.plan(state), openFirst = state.engine == null)
        state.engine.currentEvent != null -> null
        state.engine.energy == 0 && !demoMode -> EngineCommand.FinishDay
        state.engine.phase == DayPhase.READY_TO_END && !catalog.storyProgress(state).goalReadyForStory -> EngineCommand.FinishDay
        catalog.storyCampaign == null && awaitsIntroduction(state) -> EngineCommand.OpenNextEvent
        engine.blockReason(state, EngineCommand.OpenNextEvent, demoMode) in setOf(BlockReason.MustSleep, BlockReason.NoNextEvent) -> EngineCommand.FinishDay
        else -> EngineCommand.OpenNextEvent
    }
}
