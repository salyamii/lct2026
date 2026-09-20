package ru.nksk.lctapp.feature.map.ui

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import ru.nksk.lctapp.domain.location.*

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun returnsOnlyAfterCommitAndIgnoresRepeatedTaps() = runTest(dispatcher) {
        val controller = FakeController()
        val model = MapViewModel(controller).also { store.put("map", it) }
        runCurrent()
        model.select("pier")
        model.select("city")
        runCurrent()
        assertTrue(model.uiState.value.saving)
        assertFalse(model.uiState.value.finished)
        assertEquals(1, controller.calls)
        controller.commit.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.finished)
        assertEquals("pier", model.uiState.value.selectedId)
        val restored = MapViewModel(controller).also { store.put("restored", it) }
        runCurrent()
        assertEquals("pier", restored.uiState.value.selectedId)
        assertFalse(restored.uiState.value.finished)
    }

    @Test fun failureKeepsMapOpenAndAllowsRetry() = runTest(dispatcher) {
        val controller = FakeController()
        val model = MapViewModel(controller).also { store.put("map", it) }
        runCurrent()
        model.select("pier")
        controller.commit.completeExceptionally(java.io.IOException("disk full"))
        runCurrent()
        assertFalse(model.uiState.value.finished)
        assertFalse(model.uiState.value.saving)
        assertNotNull(model.uiState.value.error)
        assertEquals("city", model.uiState.value.selectedId)
        controller.commit = CompletableDeferred(Unit)
        model.select("pier")
        runCurrent()
        assertTrue(model.uiState.value.finished)
    }

    @Test fun lockedLocationNeverStartsSaving() = runTest(dispatcher) {
        val controller = FakeController()
        controller.state.value = controller.state.value.copy(availableLocations = setOf(GameLocation.CITY))
        val model = MapViewModel(controller).also { store.put("map", it) }
        runCurrent()
        model.select("pier")
        runCurrent()
        assertEquals(0, controller.calls)
        assertFalse(model.uiState.value.saving)
    }

    private class FakeController : GameLocationController {
        val state = MutableStateFlow(LocationMapState(LocationScene(), GameLocation.entries.toSet()))
        var commit = CompletableDeferred<Unit>()
        var calls = 0
        override fun observe() = state
        override suspend fun selectLocation(location: GameLocation) {
            calls++
            commit.await()
            state.value = state.value.copy(scene = state.value.scene.copy(location = location))
        }
        override suspend fun setLighting(lighting: LocationLighting) = error("Not used")
    }
}
