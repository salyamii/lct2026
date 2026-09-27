package ru.nksk.lctapp.feature.gear.ui

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem

@OptIn(ExperimentalCoroutinesApi::class)
class GearViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun acquisitionAndLossUpdateInventoryWithoutWritingOrInitializingSave() = runTest(dispatcher) {
        val games = InventoryGames(createInitialGameState())
        val content = InventoryContent()
        val model = gearModel(games, content).also { store.put("gear", it) }
        assertEquals(GearLoadState.Loading, model.uiState.value)
        advanceUntilIdle()
        assertEquals(0, (model.uiState.value as GearLoadState.Ready).inventory.itemCount)
        content.catalog = StoryContent(items = listOf(ItemDefinition("map", "Карта", "Пергамент")))
        games.state.value = games.state.value!!.copy(ownedItems = listOf(OwnedItem("map-1", "map")))
        advanceUntilIdle()
        assertEquals("Карта", (model.uiState.value as GearLoadState.Ready).inventory.storyItems.single().name)
        games.state.value = games.state.value!!.copy(ownedItems = emptyList())
        advanceUntilIdle()
        assertEquals(0, (model.uiState.value as GearLoadState.Ready).inventory.itemCount)
    }

    @Test fun catalogFailureShowsErrorAndRetryRestoresTheOwnedItem() = runTest(dispatcher) {
        val games = InventoryGames(createInitialGameState().copy(ownedItems = listOf(OwnedItem("map-1", "map"))))
        val content = InventoryContent().apply { fail = true }
        val model = gearModel(games, content).also { store.put("gear", it) }
        advanceUntilIdle()
        assertEquals(GearLoadState.Error, model.uiState.value)
        content.fail = false
        content.catalog = StoryContent(items = listOf(ItemDefinition("map", "Карта", "")))
        model.retry()
        advanceUntilIdle()
        assertEquals(1, (model.uiState.value as GearLoadState.Ready).inventory.itemCount)
    }

    @Test fun missingSaveShowsErrorInsteadOfGrantingPreviewItems() = runTest(dispatcher) {
        val model = gearModel(InventoryGames(null), InventoryContent()).also { store.put("gear", it) }
        advanceUntilIdle()
        assertEquals(GearLoadState.Error, model.uiState.value)
    }

    @Test fun observationFailureIsNotShownAsEmptyInventory() = runTest(dispatcher) {
        val games = InventoryGames(createInitialGameState()).apply { fail = true }
        val model = gearModel(games, InventoryContent()).also { store.put("gear", it) }
        advanceUntilIdle()
        assertEquals(GearLoadState.Error, model.uiState.value)
        games.fail = false
        model.retry()
        advanceUntilIdle()
        assertTrue(model.uiState.value is GearLoadState.Ready)
    }
}

private class InventoryGames(initial: GameState?) : GameRepository {
    val state = MutableStateFlow(initial)
    var fail = false
    override fun observe() = if (fail) flow<GameState?> { error("Storage unavailable") } else state
    override suspend fun read() = state.value
    override suspend fun initializeIfAbsent(initial: GameState): GameState = error("Inventory must not initialize")
    override suspend fun update(transform: (GameState) -> GameState): GameState = error("Inventory must not write")
}

private class InventoryContent : StoryContentRepository {
    var catalog = StoryContent()
    var fail = false
    override suspend fun read(): StoryContent {
        check(!fail) { "Storage unavailable" }
        return catalog
    }
    override suspend fun install(content: StoryContent) = error("Inventory must not seed content")
}

private fun gearModel(games: GameRepository, content: StoryContentRepository) = GearViewModel(games, content,
    ru.nksk.lctapp.domain.engine.GameSession(games, content,
        ru.nksk.lctapp.data.game.content.bundledGameCatalog(), createInitialGameState()))