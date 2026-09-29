package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModelStore
import java.io.IOException
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
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.PetCustomization
import ru.nksk.lctapp.domain.pet.toPetState
import ru.nksk.lctapp.domain.story.StoryDecision

@OptIn(ExperimentalCoroutinesApi::class)
class CampaignArchiveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun archivePageReadsOnlyCurrentWorldAndRestartHeadAndRecoversLostReply() = runTest(dispatcher) {
        val catalog = bundledGameCatalog()
        val initial = createInitialGameState()
        val completed = initial.copy(story = initial.story.copy(decisions = checkNotNull(catalog.storyCampaign).acts.map {
            StoryDecision("completed:${it.id}", catalog.content.choices.first { choice -> choice.eventId == it.finaleId }.id)
        }))
        val repository = ArchiveRepository(completed)
        val session = GameSession(repository, object : StoryContentRepository {
            override suspend fun read() = catalog.content
            override suspend fun install(content: StoryContent) {}
        }, catalog, initial)
        session.prepare() // The same session must later accept the new onboarding profile.
        val model = CampaignArchiveViewModel(session).also { store.put("archive", it) }
        runCurrent()
        assertTrue(model.uiState.value.canRestart)
        assertEquals(0, repository.headReads)
        repository.loseReply = true
        model.restart()
        runCurrent()
        assertNull(repository.read())
        assertFalse(model.uiState.value.restarted)
        assertEquals(1, repository.archives.size)
        val request = repository.requests.single()
        repository.loseReply = false
        model.retry()
        runCurrent()
        assertTrue(model.uiState.value.restarted)
        assertEquals(listOf(request, request), repository.requests)
        assertEquals(1, repository.headReads)
        assertEquals(1, repository.archives.size)
        session.prepare(PetCustomization("Тоша").toPetState("BANDANA"),
            savingItemId = "stargazing-tripod-v1", beginInitialAllocation = true)
        assertEquals("Тоша", repository.read()!!.pet.name)
        assertEquals("BANDANA", repository.read()!!.pet.selectedLookId)
        assertEquals(1, repository.archives.size)
    }
}

private class ArchiveRepository(initial: GameState) : GameRepository {
    private val state = MutableStateFlow<GameState?>(initial)
    val archives = mutableListOf<ArchivedGameRunSummary>()
    val requests = mutableListOf<CampaignRestartRequest>()
    var headReads = 0
    var loseReply = false
    private var receipt: CampaignRestartRequest? = null
    override fun observe() = state
    override suspend fun read() = state.value
    override suspend fun archivedRuns() = archives.toList()
    override suspend fun exportSnapshot(): GameSnapshot = error("Archive entry must not export the whole history")
    override suspend fun readSnapshotHead(): GameSnapshotHead {
        headReads++
        return GameSnapshotHead("finished-run", checkNotNull(state.value), 250)
    }
    override suspend fun initializeIfAbsent(initial: GameState) = state.value ?: initial.also { state.value = it }
    override suspend fun update(transform: (GameState) -> GameState) = transform(checkNotNull(state.value)).also { state.value = it }
    override suspend fun prepareCampaignRestart(request: CampaignRestartRequest, validateCurrent: (GameState) -> Unit) {
        requests += request
        receipt?.let { check(it == request); return }
        val current = checkNotNull(state.value)
        validateCurrent(current)
        archives += ArchivedGameRunSummary(request.expectedRunId, current.pet.name, current.engine?.day, 250)
        receipt = request
        state.value = null
        if (loseReply) throw IOException("Reply lost after archive commit")
    }
}
