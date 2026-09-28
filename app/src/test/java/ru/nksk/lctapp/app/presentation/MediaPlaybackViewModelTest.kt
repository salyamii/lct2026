package ru.nksk.lctapp.app.presentation

import androidx.lifecycle.ViewModelStore
import dagger.Lazy
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import ru.nksk.lctapp.core.ui.media.AssetMediaPlayerFactory
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.EngineRequest
import ru.nksk.lctapp.domain.engine.EngineResult
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.media.MediaPreferences
import ru.nksk.lctapp.domain.media.MediaPreferencesRepository
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPlaybackViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val catalog = bundledGameCatalog()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun mediaWaitsForPreferencesAndObservationDoesNotInitializeOrReadHistory() = runTest(dispatcher) {
        val preferences = Preferences().apply { readGate = CompletableDeferred() }
        val world = World(null)
        val model = model(world, preferences).first
        runCurrent()
        assertFalse(model.uiState.value.loaded)
        assertFalse(model.uiState.value.soundEnabled)
        assertNull(model.uiState.value.musicCueKey)
        assertEquals(0, world.initializations)
        assertEquals(0, world.reads)
        assertEquals(0, world.historyReads)
        world.states.value = createInitialGameState()
        runCurrent()
        assertNull(model.uiState.value.musicCueKey)
        preferences.readGate!!.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.loaded)
        assertTrue(model.uiState.value.soundEnabled)
        assertEquals("story.chapter_1", model.uiState.value.musicCueKey)
        assertEquals(0, world.writes)
    }

    @Test fun musicFollowsDomainActsAcrossEveryScreenAndRetainsTheFinalChapter() = runTest(dispatcher) {
        val initial = createInitialGameState()
        val world = World(initial)
        val model = model(world, Preferences()).first
        runCurrent()
        val acts = checkNotNull(catalog.storyCampaign).acts
        val decisions = mutableListOf<StoryDecision>()
        for ((index, act) in acts.withIndex()) {
            assertEquals("story.chapter_${index + 1}", model.uiState.value.musicCueKey)
            val finale = catalog.content.choices.first { it.eventId == act.finaleId }
            decisions += StoryDecision("completed-$index", finale.id)
            world.states.value = initial.copy(story = initial.story.copy(decisions = decisions.toList()))
            runCurrent()
        }
        assertEquals("story.chapter_5", model.uiState.value.musicCueKey)
        world.states.value = null
        runCurrent()
        assertNull(model.uiState.value.musicCueKey)
        assertEquals(0, world.historyReads)
        assertEquals(0, world.writes)
    }

    @Test fun failedPreferenceReadBacksOffAndRecoversTheSettingChangedElsewhere() = runTest(dispatcher) {
        val preferences = Preferences().apply { failedReads = 2 }
        val model = model(World(createInitialGameState()), preferences).first
        runCurrent()
        assertEquals(1, preferences.readAttempts)
        assertFalse(model.uiState.value.loaded)
        assertFalse(model.uiState.value.soundEnabled)
        assertNotNull(model.uiState.value.error)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, preferences.readAttempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, preferences.readAttempts)
        preferences.publish(false)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(2, preferences.readAttempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(3, preferences.readAttempts)
        assertTrue(model.uiState.value.loaded)
        assertFalse(model.uiState.value.soundEnabled)
        assertNull(model.uiState.value.error)
        preferences.publish(true)
        runCurrent()
        assertTrue(model.uiState.value.soundEnabled)
        assertTrue(preferences.writes.isEmpty())
    }

    @Test fun runtimeReadFailureMutesEverythingWithoutWritingAndExplicitRetrySkipsBackoff() = runTest(dispatcher) {
        val preferences = Preferences()
        val model = model(World(createInitialGameState()), preferences).first
        runCurrent()
        assertTrue(model.uiState.value.soundEnabled)
        assertNotNull(model.uiState.value.musicCueKey)
        preferences.values.value = Result.failure(IOException("Read failed"))
        runCurrent()
        assertFalse(model.uiState.value.loaded)
        assertFalse(model.uiState.value.soundEnabled)
        assertNull(model.uiState.value.musicCueKey)
        preferences.publish(true)
        model.retry()
        runCurrent()
        assertEquals(2, preferences.readAttempts)
        assertTrue(model.uiState.value.loaded)
        assertTrue(model.uiState.value.soundEnabled)
        assertEquals("story.chapter_1", model.uiState.value.musicCueKey)
        assertTrue(preferences.writes.isEmpty())
    }

    @Test fun failedIntroToggleCanRetryAndASettingsUpdateCanConfirmItWithoutAnotherWrite() = runTest(dispatcher) {
        val preferences = Preferences().apply { failWrites = true }
        val model = model(World(null), preferences).first
        runCurrent()
        model.setSoundEnabled(false)
        model.setSoundEnabled(true)
        runCurrent()
        assertEquals(listOf(false), preferences.writes)
        assertTrue(model.uiState.value.soundEnabled)
        assertNotNull(model.uiState.value.error)
        preferences.publish(false) // The same preference was successfully saved by Settings.
        runCurrent()
        assertFalse(model.uiState.value.soundEnabled)
        assertNull(model.uiState.value.error)
        model.retry()
        runCurrent()
        assertEquals(listOf(false), preferences.writes)
        preferences.failWrites = false
        model.setSoundEnabled(true)
        runCurrent()
        assertTrue(model.uiState.value.soundEnabled)
        assertEquals(listOf(false, true), preferences.writes)
    }

    @Test fun mutedPaymentsAreDiscardedInsteadOfPlayingWhenSoundIsEnabledLater() = runTest(dispatcher) {
        val initial = createInitialGameState().copy(
            economy = EconomyState(BudgetPlan(100, 0, 0, 0)),
            story = StoryState(catalog.storyDayId, 0, null, emptyList()),
            engine = EngineState(catalog.rules.id, 0, 1, DayPhase.RUNNING, 0, 5, false, null, 100, emptyList(), emptyList()),
        )
        val world = World(initial)
        val preferences = Preferences().apply { publish(false) }
        val (model, session) = model(world, preferences)
        val played = mutableListOf<MediaCueRequest>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.actionCues.collect { played += it } }
        runCurrent()
        val meal = catalog.mealPolicy.basicMeal.id
        assertTrue(session.engine.dispatch(EngineRequest("muted", 0, EngineCommand.Feed(meal))) is EngineResult.Applied)
        runCurrent()
        assertTrue(played.isEmpty())
        preferences.publish(true)
        runCurrent()
        assertTrue(played.isEmpty())
        world.states.value = initial.copy(engine = initial.engine!!.copy(revision = 1))
        assertTrue(session.engine.dispatch(EngineRequest("audible", 1, EngineCommand.Feed(meal))) is EngineResult.Applied)
        runCurrent()
        assertEquals(listOf(MediaCueRequest("audible", listOf("sound.payment"))), played)
    }

    private fun model(world: World, preferences: Preferences): Pair<MediaPlaybackViewModel, GameSession> {
        val session = GameSession(world, object : StoryContentRepository {
            override suspend fun read(): StoryContent = error("Media must not load content through persistence")
            override suspend fun install(content: StoryContent) = error("Media must not initialize the session")
        }, catalog, createInitialGameState())
        val factory = Lazy<AssetMediaPlayerFactory> { error("Playback is owned by the host, not its state model") }
        return MediaPlaybackViewModel(preferences, factory, session, dispatcher).also { store.put("media", it) } to session
    }

    private class Preferences : MediaPreferencesRepository {
        val values = MutableStateFlow(Result.success(MediaPreferences()))
        var readAttempts = 0
        var failedReads = 0
        var readGate: CompletableDeferred<Unit>? = null
        var failWrites = false
        val writes = mutableListOf<Boolean>()
        fun publish(enabled: Boolean) { values.value = Result.success(MediaPreferences(enabled)) }
        override fun observe() = flow {
            readAttempts++
            if (failedReads > 0) { failedReads--; throw IOException("Read failed") }
            readGate?.await()
            emitAll(values.map { it.getOrThrow() })
        }
        override suspend fun read() = observe().first()
        override suspend fun setSoundEnabled(enabled: Boolean) {
            writes += enabled
            if (failWrites) throw IOException("Write failed")
            publish(enabled)
        }
    }

    private class World(initial: GameState?) : GameRepository {
        val states = MutableStateFlow(initial)
        var initializations = 0
        var writes = 0
        var reads = 0
        var historyReads = 0
        override fun observe() = states
        override suspend fun read(): GameState? { reads++; return states.value }
        override suspend fun initializeIfAbsent(initial: GameState): GameState {
            initializations++
            return checkNotNull(states.value)
        }
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            writes++
            return transform(checkNotNull(states.value)).also { states.value = it }
        }
        override suspend fun readHistory(): List<ru.nksk.lctapp.domain.history.AuditEntry> {
            historyReads++
            error("Music must not read history")
        }
    }
}
