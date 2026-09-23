package ru.nksk.lctapp.app.navigation

import ru.nksk.lctapp.domain.economy.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.data.game.content.STARS_GOAL
import ru.nksk.lctapp.data.game.content.TOWER_GOAL
import ru.nksk.lctapp.data.game.content.MAP_GOAL
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.onboarding.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun openingOnboardingDoesNotCreateGameOrStartStory() = runTest(dispatcher) {
        val repository = StartupRepository()
        val model = model(repository)
        advanceUntilIdle()
        assertEquals(AppStartupState.Choose(), model.uiState.value)
        assertNull(repository.read())
        model.startAdventure()
        model.startAdventure()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Customize)
        assertNull(repository.read())
        assertEquals("", (model.uiState.value as AppStartupState.Customize).draft.name)
        model.finishCustomization()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Customize)
        assertNull(repository.read())
        val profile = PetCustomization(name = "Искорка", fur = PetFur.Sand, temperament = PetTemperament.Confident)
        model.editCustomization(profile)
        model.finishCustomization()
        model.finishCustomization()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Accessories)
        assertNull(repository.read())
        model.selectAccessory("BANDANA")
        model.confirmAccessory()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.GoalBriefing)
        model.continueToGoals()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.GoalSelection)
        model.confirmGoal()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.GoalSelection)
        model.selectGoal(STARS_GOAL)
        advanceUntilIdle()
        model.confirmGoal()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Introduction)
        assertNull(repository.read())
        model.finishOnboarding()
        model.finishOnboarding()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(profile.toPetState("BANDANA"), repository.read()!!.pet)
        assertEquals(PetAge.CUB, repository.read()!!.pet.age)
        assertEquals("BANDANA", repository.read()!!.pet.selectedLookId)
        assertNull(repository.read()!!.engine)
        assertEquals(STARS_GOAL, repository.read()!!.selectedGoalId)
        assertEquals(1, repository.initializations)
        assertEquals(100L, repository.read()!!.economy.balance)
        assertEquals(100L, repository.read()!!.economy.unallocated)
        assertEquals(BudgetPlan(0, 0, 0, 0), repository.read()!!.economy.plan)
        assertEquals(BudgetPlanningReason.INITIAL, repository.read()!!.economy.planning!!.reason)
        assertEquals(BudgetPlanningStage.RECEIPT, repository.read()!!.economy.planning!!.stage)
    }

    @Test fun existingSaveSkipsOnboardingWithoutChangingProgress() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val saved = initial.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 37)))
        val repository = StartupRepository(saved)
        val drafts = MemoryDrafts()
        drafts.save(OnboardingDraft(PetCustomization(), OnboardingStep.Introduction, "PLAIN", STARS_GOAL))
        val model = model(repository, drafts)
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(saved, repository.read())
        assertEquals(0, repository.initializations)
    }

    @Test fun readFailureNeverStartsNewGameAndCanBeRetried() = runTest(dispatcher) {
        val repository = StartupRepository(createInitialGameState())
        repository.readFailure = IllegalStateException("Unavailable")
        val model = model(repository)
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Error)
        model.startAdventure()
        advanceUntilIdle()
        assertEquals(0, repository.initializations)
        repository.readFailure = null
        model.retry()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
    }

    @Test fun saveFailureStaysOnOnboardingAndRetryDoesNotReplaceAnExistingSave() = runTest(dispatcher) {
        val repository = StartupRepository()
        val model = model(repository)
        advanceUntilIdle()
        model.startAdventure()
        advanceUntilIdle()
        model.editName("Искорка")
        model.finishCustomization()
        advanceUntilIdle()
        model.confirmAccessory()
        advanceUntilIdle()
        model.continueToGoals()
        advanceUntilIdle()
        model.selectGoal(STARS_GOAL)
        advanceUntilIdle()
        model.confirmGoal()
        advanceUntilIdle()
        repository.writeFailure = IllegalStateException("Disk full")
        model.finishOnboarding()
        advanceUntilIdle()
        assertTrue((model.uiState.value as AppStartupState.Introduction).failed)
        assertNull(repository.read())
        repository.writeFailure = null
        val saved = createInitialGameState().let { it.copy(economy = EconomyState(BudgetPlan(0, 0, 0, 59))) }
        repository.state.value = saved
        model.finishOnboarding()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(saved, repository.read())
    }

    @Test fun backResetsDraftAndDoesNotCreateGame() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        val model = model(repository, drafts)
        advanceUntilIdle()
        model.startAdventure()
        advanceUntilIdle()
        model.editCustomization(PetCustomization(name = "Другой", fur = PetFur.Russet))
        model.backToCharacters()
        advanceUntilIdle()
        assertEquals(AppStartupState.Choose(), model.uiState.value)
        assertNull(drafts.read())
        assertNull(repository.read())
        model.startAdventure()
        advanceUntilIdle()
        assertEquals(PetCustomization(name = ""), (model.uiState.value as AppStartupState.Customize).draft)
    }

    @Test fun unfinishedCustomizationResumesAfterNewViewModel() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        val model = model(repository, drafts)
        advanceUntilIdle()
        model.startAdventure()
        advanceUntilIdle()
        val edited = PetCustomization(name = "", fur = PetFur.Russet)
        model.editCustomization(edited)
        advanceUntilIdle()
        val restored = model(repository, drafts)
        advanceUntilIdle()
        assertEquals(edited, (restored.uiState.value as AppStartupState.Customize).draft)
        restored.finishCustomization()
        advanceUntilIdle()
        assertNull(repository.read())
    }

    @Test fun accessoryDraftResumesAndBackPreservesProfile() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        val model = model(repository, drafts)
        advanceUntilIdle()
        model.startAdventure()
        advanceUntilIdle()
        model.editName("Искорка")
        model.editFur(PetFur.Sand)
        model.finishCustomization()
        advanceUntilIdle()
        model.selectAccessory("LANTERN")
        model.confirmAccessory()
        advanceUntilIdle()
        assertNull(repository.read())
        val restored = model(repository, drafts)
        advanceUntilIdle()
        assertEquals("LANTERN", (restored.uiState.value as AppStartupState.Accessories).draft.accessoryId)
        restored.backToCustomization()
        advanceUntilIdle()
        assertEquals(PetFur.Sand, (restored.uiState.value as AppStartupState.Customize).draft.fur)
        restored.finishCustomization()
        advanceUntilIdle()
        restored.selectAccessory("PLAIN")
        restored.confirmAccessory()
        advanceUntilIdle()
        assertNull(repository.read())
        val intro = model(repository, drafts)
        advanceUntilIdle()
        assertEquals(OnboardingStep.GoalBriefing, (intro.uiState.value as AppStartupState.GoalBriefing).draft.step)
        intro.backToAccessories()
        advanceUntilIdle()
        assertEquals("PLAIN", (intro.uiState.value as AppStartupState.Accessories).draft.accessoryId)
        intro.confirmAccessory()
        advanceUntilIdle()
        intro.continueToGoals()
        advanceUntilIdle()
        intro.selectGoal(TOWER_GOAL)
        advanceUntilIdle()
        intro.confirmGoal()
        advanceUntilIdle()
        intro.finishOnboarding()
        advanceUntilIdle()
        assertEquals("PLAIN", repository.read()!!.pet.selectedLookId)
        assertEquals("Искорка", repository.read()!!.pet.name)
    }

    @Test fun goalSelectionAndConfirmationResumeWithoutStartingGame() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        drafts.save(OnboardingDraft(PetCustomization(), OnboardingStep.GoalSelection))
        val first = model(repository, drafts)
        advanceUntilIdle()
        first.selectGoal(MAP_GOAL)
        first.selectGoal("unknown")
        advanceUntilIdle()
        assertNull(drafts.read()!!.goalId)
        first.selectGoal(TOWER_GOAL)
        advanceUntilIdle()
        val restored = model(repository, drafts)
        advanceUntilIdle()
        assertEquals(TOWER_GOAL, (restored.uiState.value as AppStartupState.GoalSelection).draft.goalId)
        restored.confirmGoal()
        restored.confirmGoal()
        advanceUntilIdle()
        val confirmation = model(repository, drafts)
        advanceUntilIdle()
        assertEquals(TOWER_GOAL, (confirmation.uiState.value as AppStartupState.Introduction).draft.goalId)
        assertNull(repository.read())
        confirmation.backToGoals()
        advanceUntilIdle()
        confirmation.backToGoalBriefing()
        advanceUntilIdle()
        confirmation.backToAccessories()
        advanceUntilIdle()
        confirmation.backToCustomization()
        advanceUntilIdle()
        confirmation.finishCustomization()
        advanceUntilIdle()
        confirmation.confirmAccessory()
        advanceUntilIdle()
        confirmation.continueToGoals()
        advanceUntilIdle()
        assertEquals(TOWER_GOAL, (confirmation.uiState.value as AppStartupState.GoalSelection).draft.goalId)
        confirmation.selectGoal(STARS_GOAL)
        advanceUntilIdle()
        confirmation.confirmGoal()
        advanceUntilIdle()
        confirmation.finishOnboarding()
        advanceUntilIdle()
        assertEquals(STARS_GOAL, repository.read()!!.selectedGoalId)
        assertNull(repository.read()!!.engine)
    }

    @Test fun failedGoalSaveStaysVisibleAndSameSelectionCanBeRetried() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        drafts.save(OnboardingDraft(PetCustomization(), OnboardingStep.GoalSelection))
        val model = model(repository, drafts)
        advanceUntilIdle()
        drafts.failWrites = true
        model.selectGoal(STARS_GOAL)
        advanceUntilIdle()
        assertTrue((model.uiState.value as AppStartupState.GoalSelection).failed)
        assertNull(drafts.read()!!.goalId)
        assertNull(repository.read())
        drafts.failWrites = false
        model.selectGoal(STARS_GOAL)
        advanceUntilIdle()
        assertEquals(STARS_GOAL, drafts.read()!!.goalId)
        model.confirmGoal()
        advanceUntilIdle()
        assertTrue(model.uiState.value is AppStartupState.Introduction)
    }

    @Test fun goalBriefingRestoresAndBackPreservesAllChoicesWithoutCreatingGame() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        val profile = PetCustomization(name = "Искорка", fur = PetFur.Sand)
        val draft = OnboardingDraft(profile, OnboardingStep.Accessories, "BANDANA", TOWER_GOAL)
        drafts.save(draft)
        val first = model(repository, drafts)
        advanceUntilIdle()
        first.confirmAccessory()
        first.confirmAccessory()
        advanceUntilIdle()
        val restored = model(repository, drafts)
        advanceUntilIdle()
        assertEquals(draft.copy(step = OnboardingStep.GoalBriefing),
            (restored.uiState.value as AppStartupState.GoalBriefing).draft)
        restored.finishOnboarding()
        restored.selectGoal(STARS_GOAL)
        restored.confirmGoal()
        advanceUntilIdle()
        assertTrue(restored.uiState.value is AppStartupState.GoalBriefing)
        restored.continueToGoals()
        restored.continueToGoals()
        advanceUntilIdle()
        assertEquals(TOWER_GOAL, (restored.uiState.value as AppStartupState.GoalSelection).draft.goalId)
        restored.backToGoalBriefing()
        advanceUntilIdle()
        restored.backToAccessories()
        advanceUntilIdle()
        assertEquals(draft, (restored.uiState.value as AppStartupState.Accessories).draft)
        assertEquals(draft, drafts.read())
        assertNull(repository.read())
    }

    @Test fun failedBriefingContinueCanBeRetriedWithoutLosingDraft() = runTest(dispatcher) {
        val repository = StartupRepository()
        val drafts = MemoryDrafts()
        val draft = OnboardingDraft(PetCustomization(), OnboardingStep.GoalBriefing, "BANDANA", TOWER_GOAL)
        drafts.save(draft)
        val model = model(repository, drafts)
        advanceUntilIdle()
        drafts.failWrites = true
        model.continueToGoals()
        advanceUntilIdle()
        assertTrue((model.uiState.value as AppStartupState.GoalBriefing).failed)
        assertEquals(draft, drafts.read())
        assertNull(repository.read())
        drafts.failWrites = false
        model.continueToGoals()
        advanceUntilIdle()
        assertEquals(draft.copy(step = OnboardingStep.GoalSelection),
            (model.uiState.value as AppStartupState.GoalSelection).draft)
    }

    @Test fun sessionRejectsUnavailableStartingGoalWithoutCreatingSave() = runTest(dispatcher) {
        val repository = StartupRepository()
        val session = session(repository)
        try {
            session.prepare(PetCustomization().toPetState("PLAIN"), MAP_GOAL)
            fail("A locked project must not initialize a game")
        } catch (_: IllegalArgumentException) {
            assertNull(repository.read())
        }
        session.prepare(PetCustomization().toPetState("PLAIN"), TOWER_GOAL)
        assertEquals(TOWER_GOAL, repository.read()!!.selectedGoalId)
        assertNull(repository.read()!!.engine)
    }

    private fun model(repository: GameRepository, drafts: OnboardingDraftRepository = MemoryDrafts()) =
        AppStartupViewModel(session(repository), drafts)

    private fun session(repository: GameRepository) = GameSession(
        repository, object : StoryContentRepository {
            private var content = StoryContent()
            override suspend fun read() = content
            override suspend fun install(content: StoryContent) { this.content = content }
        }, bundledGameCatalog(), createInitialGameState(),
    )
}

private class StartupRepository(initial: GameState? = null) : GameRepository {
    val state = MutableStateFlow(initial)
    var initializations = 0
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    override fun observe() = state
    override suspend fun read(): GameState? {
        readFailure?.let { throw it }
        return state.value
    }
    override suspend fun initializeIfAbsent(initial: GameState): GameState {
        writeFailure?.let { throw it }
        initializations++
        return state.value ?: initial.also { state.value = it }
    }
    override suspend fun update(transform: (GameState) -> GameState) =
        transform(requireNotNull(state.value)).also { state.value = it }
}

private class MemoryDrafts : OnboardingDraftRepository {
    private var draft: OnboardingDraft? = null
    var failWrites = false
    override suspend fun read() = draft
    override suspend fun save(draft: OnboardingDraft) {
        check(!failWrites) { "Disk full" }
        this.draft = draft
    }
    override suspend fun clear() { draft = null }
}
