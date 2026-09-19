package ru.nksk.lctapp.app.navigation

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
        assertTrue(model.uiState.value is AppStartupState.Introduction)
        assertNull(repository.read())
        model.finishOnboarding()
        model.finishOnboarding()
        advanceUntilIdle()
        assertEquals(AppStartupState.Ready, model.uiState.value)
        assertEquals(profile, repository.read()!!.pet.customization)
        assertEquals(PetAge.Cub, repository.read()!!.pet.customization!!.age)
        assertEquals("BANDANA", repository.read()!!.pet.selectedLookId)
        assertNull(repository.read()!!.engine)
        assertEquals(1, repository.initializations)
    }

    @Test fun existingSaveSkipsOnboardingWithoutChangingProgress() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val saved = initial.copy(economy = initial.economy.copy(balance = 37))
        val repository = StartupRepository(saved)
        val model = model(repository)
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
        repository.writeFailure = IllegalStateException("Disk full")
        model.finishOnboarding()
        advanceUntilIdle()
        assertTrue((model.uiState.value as AppStartupState.Introduction).failed)
        assertNull(repository.read())
        repository.writeFailure = null
        val saved = createInitialGameState().let { it.copy(economy = it.economy.copy(balance = 59)) }
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
        assertEquals(OnboardingStep.Introduction, (intro.uiState.value as AppStartupState.Introduction).draft.step)
        intro.backToAccessories()
        advanceUntilIdle()
        assertEquals("PLAIN", (intro.uiState.value as AppStartupState.Accessories).draft.accessoryId)
        intro.confirmAccessory()
        advanceUntilIdle()
        intro.finishOnboarding()
        advanceUntilIdle()
        assertEquals("PLAIN", repository.read()!!.pet.selectedLookId)
        assertEquals("Искорка", repository.read()!!.pet.customization!!.name)
    }

    private fun model(repository: GameRepository, drafts: OnboardingDraftRepository = MemoryDrafts()) = AppStartupViewModel(GameSession(
        repository, object : StoryContentRepository {
            private var content = StoryContent()
            override suspend fun read() = content
            override suspend fun install(content: StoryContent) { this.content = content }
        }, bundledGameCatalog(), createInitialGameState(),
    ), drafts)
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
    override suspend fun read() = draft
    override suspend fun save(draft: OnboardingDraft) { this.draft = draft }
    override suspend fun clear() { draft = null }
}
