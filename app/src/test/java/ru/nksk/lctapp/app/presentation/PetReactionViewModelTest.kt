package ru.nksk.lctapp.app.presentation

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.EventOccurrence
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetColor
import ru.nksk.lctapp.domain.pet.PetVisualState

@OptIn(ExperimentalCoroutinesApi::class)
class PetReactionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val catalog = bundledGameCatalog()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    @Test fun initialReactionExpiresAfterFourSecondsWithoutWritingAnyWorldState() = runTest(dispatcher) {
        val original = game(PetVisualState.HAPPY)
        val repository = ReactionRepository(original)
        val content = ReadOnlyContent()
        val model = model(repository, content)
        runCurrent()
        assertTrue(checkNotNull(model.uiState.value).reactionVisible)

        advanceTimeBy(3_999)
        runCurrent()
        assertTrue(checkNotNull(model.uiState.value).reactionVisible)
        advanceTimeBy(1)
        runCurrent()

        val reaction = checkNotNull(model.uiState.value)
        assertFalse(reaction.reactionVisible)
        assertEquals(original.pet, reaction.rawPet)
        assertEquals(original.pet, reaction.effectivePet)
        assertEquals(original, repository.read()) // Includes satiety, fatigue, energy, day and money.
        assertEquals(0, repository.writes)
        assertEquals(0, content.installCalls)
    }

    @Test fun aDifferentReactionCancelsTheOldDeadline() = runTest(dispatcher) {
        val original = game(PetVisualState.HAPPY)
        val repository = ReactionRepository(original)
        val model = model(repository)
        runCurrent()
        advanceTimeBy(3_000)
        repository.emit(original.copy(pet = original.pet.transitionTo(PetVisualState.THINKING)))
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(checkNotNull(model.uiState.value).reactionVisible) // The old 4-second deadline is cancelled.
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(checkNotNull(model.uiState.value).reactionVisible)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        assertEquals(PetVisualState.THINKING, checkNotNull(model.uiState.value).effectivePet.visualState)
        assertEquals(0, repository.writes)
    }

    @Test fun lookNameColorAndDayUpdatesKeepTheirLatestValuesWithoutRestartingTheWindow() = runTest(dispatcher) {
        val original = game(PetVisualState.HAPPY)
        val repository = ReactionRepository(original)
        val model = model(repository)
        runCurrent()
        advanceTimeBy(3_000)
        val changed = original.copy(
            pet = original.pet.copy(name = "Лис", color = PetColor.SAND, selectedLookId = "BACKPACK"),
            engine = original.engine!!.copy(day = 4, revision = 19),
        )
        repository.emit(changed)
        runCurrent()
        assertEquals(changed.pet, checkNotNull(model.uiState.value).effectivePet)
        assertTrue(checkNotNull(model.uiState.value).reactionVisible)

        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        assertEquals(changed.pet, checkNotNull(model.uiState.value).rawPet)
        assertEquals(changed.pet, checkNotNull(model.uiState.value).effectivePet)
        repository.emit(changed.copy(engine = changed.engine!!.copy(revision = 20)))
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        assertEquals(0, repository.writes)
    }

    @Test fun normalAndMissingSavesCancelTheClockWithoutResettingThePet() = runTest(dispatcher) {
        val original = game(PetVisualState.NORMAL)
        val repository = ReactionRepository(original)
        val model = model(repository)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        repository.emit(original.copy(pet = original.pet.transitionTo(PetVisualState.UPSET)))
        runCurrent()
        advanceTimeBy(1_000)
        repository.emit(original)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        repository.emit(null)
        runCurrent()
        assertNull(model.uiState.value)

        advanceTimeBy(2_000)
        repository.emit(original.copy(pet = original.pet.transitionTo(PetVisualState.UPSET)))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(checkNotNull(model.uiState.value).reactionVisible)
        advanceTimeBy(3_000)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        assertEquals(PetVisualState.UPSET, repository.read()!!.pet.visualState)
        assertEquals(0, repository.writes)
    }

    @Test fun legacyOpenedIllnessUsesEffectiveVisualStateWithoutChangingRawPet() = runTest(dispatcher) {
        val illness = catalog.policies.entries.first { it.value.requiresPetHelp }.key
        val original = game(PetVisualState.NORMAL).let { state -> state.copy(
            story = state.story.copy(activeEventId = illness),
            engine = state.engine!!.copy(events = listOf(EventOccurrence(
                "illness", illness, EventOrigin.SCHEDULE, EventStatus.ACTIVE))),
        ) }
        val repository = ReactionRepository(original)
        val model = model(repository)
        runCurrent()
        val reaction = checkNotNull(model.uiState.value)
        assertEquals(PetVisualState.NORMAL, reaction.rawPet.visualState)
        assertEquals(PetVisualState.NEEDS_HELP, reaction.effectivePet.visualState)
        assertTrue(reaction.reactionVisible)
        advanceTimeBy(4_000)
        runCurrent()
        assertFalse(checkNotNull(model.uiState.value).reactionVisible)
        assertEquals(original, repository.read())
        assertEquals(0, repository.writes)
    }

    @Test fun observationFailureIsUnavailableAndHasOnlyBoundedDelayedRetries() = runTest(dispatcher) {
        val repository = ReactionRepository(game(PetVisualState.HAPPY)).apply {
            observationFailure = IllegalStateException("Storage unavailable")
        }
        val model = model(repository)
        runCurrent()
        assertNull(model.uiState.value)
        assertEquals(1, repository.subscriptions)
        advanceTimeBy(20_000)
        runCurrent()
        assertNull(model.uiState.value)
        assertEquals(3, repository.subscriptions)
        assertEquals(0, repository.writes)
    }

    private fun game(visual: PetVisualState): GameState = createInitialGameState().let { initial -> initial.copy(
        pet = initial.pet.transitionTo(visual), satiety = 7, fatigue = 9,
        engine = EngineState(catalog.rules.id, 3, 3, DayPhase.RUNNING, 2, 3, false,
            3, initial.economy.balance, emptyList(), emptyList()),
    ) }

    private fun model(repository: ReactionRepository, content: ReadOnlyContent = ReadOnlyContent()): PetReactionViewModel {
        val session = GameSession(repository, content, catalog, createInitialGameState())
        return PetReactionViewModel(session).also { model ->
            stores += ViewModelStore().also { it.put("reaction", model) }
        }
    }
}

private class ReactionRepository(initial: GameState?) : GameRepository {
    private val state = MutableStateFlow(initial)
    var writes = 0
    var subscriptions = 0
    var observationFailure: Exception? = null

    fun emit(game: GameState?) { state.value = game }
    override fun observe(): Flow<GameState?> = flow {
        subscriptions++
        observationFailure?.let { throw it }
        emitAll(state)
    }
    override suspend fun read(): GameState? = state.value
    override suspend fun initializeIfAbsent(initial: GameState): GameState {
        writes++
        error("A presentation clock cannot initialize the world")
    }
    override suspend fun update(transform: (GameState) -> GameState): GameState {
        writes++
        error("A presentation clock cannot update the world")
    }
}

private class ReadOnlyContent : StoryContentRepository {
    var installCalls = 0
    override suspend fun read() = StoryContent()
    override suspend fun install(content: StoryContent) {
        installCalls++
        error("A presentation clock cannot install or prepare the world")
    }
}
