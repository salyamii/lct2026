package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModelStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry

@OptIn(ExperimentalCoroutinesApi::class)
class ReflectionOpeningTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun failedFirstOpeningCanRetryAndDoesNotMarkReflectionConfiguredOrRewriteGame() = runTest(dispatcher) {
        val catalog = bundledGameCatalog()
        val initial = createInitialGameState().copy(
            engine = EngineState(catalog.rules.id, 1, 9, DayPhase.FINISHED, 0, catalog.rules.fullEnergy,
                true, null, 100, emptyList(), emptyList()),
        )
        val repository = ReflectionHistoryRepository(initial)
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read(): StoryContent = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        val model = ReflectionViewModel(session)
        store.put("reflection", model)
        model.setActive(true)
        model.uiState.first { !it.loading }
        val beforeOpening = repository.state.value
        repository.failNextHistoryRead = true

        model.openReflection(9)
        val failed = model.uiState.first { !it.busy }
        assertEquals(9, failed.reflectionDay)
        assertNull(failed.chronoscopeStep)
        assertNotNull(failed.error)

        // Resuming observation must not dismiss an unsaved action's retry message.
        model.setActive(false)
        runCurrent()
        model.setActive(true)
        runCurrent()
        assertEquals(failed.error, model.uiState.value.error)
        assertNull(model.uiState.value.chronoscopeStep)

        model.onAction(ReflectionAction.Retry)
        val recovered = model.uiState.first { !it.busy }
        assertEquals(ChronoscopeStep.MOMENTS, recovered.chronoscopeStep)
        assertEquals(9, recovered.reflectionDay)
        assertNotNull(recovered.timeMachine)
        assertNull(recovered.error)
        assertEquals(beforeOpening, repository.state.value)

        // Recomposition after successful opening must not reset the user's viewing scope.
        model.onAction(ReflectionAction.SetReflectionScope(ReflectionScope.WEEK))
        model.uiState.first { !it.busy }
        val reads = repository.historyReads
        model.openReflection(9)
        assertEquals(ReflectionScope.WEEK, model.uiState.value.reflectionScope)
        assertEquals(reads, repository.historyReads)
    }

    @Test fun retryAfterInitialHistoryFailureRestoresGameAndKeepsObservingChanges() = runTest(dispatcher) {
        val (repository, session) = fixture()
        repository.failNextHistoryRead = true
        val model = ReflectionViewModel(session)
        store.put("reflection", model)
        model.setActive(true)
        val failed = model.uiState.first { !it.loading }
        assertNotNull(failed.error)
        assertNull(failed.realGame)

        // The route supplies its filter after loading, but does not hide the read failure.
        model.openReflection(9)
        assertNotNull(model.uiState.value.error)
        model.onAction(ReflectionAction.Retry)
        val recovered = model.uiState.first { !it.busy && it.chronoscopeStep == ChronoscopeStep.MOMENTS }
        assertEquals(repository.state.value, recovered.realGame)
        assertNull(recovered.error)

        val latest = repository.state.value.copy(pet = repository.state.value.pet.copy(name = "После повтора"))
        repository.state.value = latest
        assertEquals(latest, model.uiState.first { it.realGame == latest }.realGame)
        assertEquals(1, repository.state.subscriptionCount.value)
    }

    @Test fun hiddenReflectionStopsHistoryReadsAndResumesFromLatestGame() = runTest(dispatcher) {
        val (repository, session) = fixture()
        val model = ReflectionViewModel(session)
        store.put("reflection", model)
        model.setActive(true)
        val before = model.uiState.first { !it.loading }.realGame
        model.setActive(false)
        runCurrent()
        val reads = repository.historyReads
        val latest = repository.state.value.copy(pet = repository.state.value.pet.copy(name = "Пока экран скрыт"))
        repository.state.value = latest
        runCurrent()
        assertEquals(reads, repository.historyReads)
        assertEquals(before, model.uiState.value.realGame)
        assertEquals(0, repository.state.subscriptionCount.value)

        model.setActive(true)
        assertEquals(latest, model.uiState.first { it.realGame == latest }.realGame)
        assertEquals(reads + 1, repository.historyReads)
        assertEquals(1, repository.state.subscriptionCount.value)
    }

    @Test fun hiddenHistoryStopsReportReadsAndResumesObservation() = runTest(dispatcher) {
        val (repository, session) = fixture()
        val model = LearningHistoryViewModel(session)
        store.put("history", model)
        model.setActive(true)
        model.uiState.first { !it.loading }
        model.setActive(false)
        runCurrent()
        val reads = repository.historyReads
        repository.state.value = repository.state.value.copy(pet = repository.state.value.pet.copy(name = "Другой день"))
        runCurrent()
        assertEquals(reads, repository.historyReads)
        assertEquals(0, repository.state.subscriptionCount.value)

        model.setActive(true)
        model.uiState.first { !it.loading }
        assertEquals(reads + 1, repository.historyReads)
        assertEquals(1, repository.state.subscriptionCount.value)
    }

    private fun fixture(): Pair<ReflectionHistoryRepository, GameSession> {
        val catalog = bundledGameCatalog()
        val initial = createInitialGameState().copy(
            engine = EngineState(catalog.rules.id, 1, 9, DayPhase.FINISHED, 0, catalog.rules.fullEnergy,
                true, null, 100, emptyList(), emptyList()),
        )
        val repository = ReflectionHistoryRepository(initial)
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read(): StoryContent = catalog.content
            override suspend fun install(content: StoryContent) = Unit
        }, catalog, initial)
        return repository to session
    }
}

private class ReflectionHistoryRepository(initial: GameState) : GameRepository {
    val state = MutableStateFlow(initial)
    var failNextHistoryRead = false
    var historyReads = 0

    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState) = state.value
    override suspend fun update(transform: (GameState) -> GameState): GameState = transform(state.value).also { state.value = it }
    override suspend fun readHistory(): List<AuditEntry> {
        historyReads += 1
        if (failNextHistoryRead) {
            failNextHistoryRead = false
            throw IOException("History temporarily unavailable")
        }
        return emptyList()
    }
}
