package ru.nksk.lctapp.domain.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.*
import ru.nksk.lctapp.domain.pet.*
import ru.nksk.lctapp.domain.story.*

class GameLocationControllerTest {
    @Test fun selectionReadsLatestSaveAndPreservesOtherFields() = runTest {
        val repository = FakeRepository(initial())
        val controller = DefaultGameLocationController(repository, AllLocationsAvailable)
        controller.observe().first() // an older UI snapshot must not own the next write
        repository.state.value = initial().copy(fatigue = 95, economy = EconomyState(753, BudgetPlan(1, 2, 3, 4)))
        val latest = repository.state.value!!
        controller.selectLocation(GameLocation.PIER)
        assertEquals(latest.copy(locationScene = latest.locationScene.copy(location = GameLocation.PIER)), repository.read())
    }

    @Test fun lightingIsExplicitAndIndependentOfFatigueAndLocation() = runTest {
        val repository = FakeRepository(initial().copy(fatigue = 100))
        val controller = DefaultGameLocationController(repository, AllLocationsAvailable)
        assertEquals(LocationLighting.DAY, controller.observe().first()!!.scene.lighting)
        controller.setLighting(LocationLighting.EVENING)
        controller.selectLocation(GameLocation.WINDMILL)
        assertEquals(LocationScene(GameLocation.WINDMILL, LocationLighting.EVENING), repository.read()!!.locationScene)
        val before = repository.read()!!
        controller.setLighting(LocationLighting.DAY)
        assertEquals(before.copy(locationScene = before.locationScene.copy(lighting = LocationLighting.DAY)), repository.read())
    }

    @Test fun lockedLocationIsRecheckedInsideTransaction() = runTest {
        val repository = FakeRepository(initial())
        val policy = LocationAccessPolicy { _, game -> game.fatigue == 0 }
        val controller = DefaultGameLocationController(repository, policy)
        assertTrue(GameLocation.GATES in controller.observe().first()!!.availableLocations)
        repository.state.value = initial().copy(fatigue = 1) // synthetic policy for this regression only
        val before = repository.read()
        try { controller.selectLocation(GameLocation.GATES); fail("Must reject locked selection") }
        catch (_: LocationUnavailableException) { }
        assertEquals(before, repository.read())
    }

    @Test fun writeFailureDoesNotPublishNewLocation() = runTest {
        val repository = FakeRepository(initial())
        val controller = DefaultGameLocationController(repository, AllLocationsAvailable)
        repository.failWrite = true
        try { controller.selectLocation(GameLocation.FAIR); fail("Must propagate storage failure") }
        catch (_: java.io.IOException) { }
        assertEquals(initial(), repository.read())
    }

    private fun initial() = GameState(PetState("PLAIN", PetVisualState.NORMAL),
        EconomyState(100, BudgetPlan(0, 0, 0, 0)), StoryState(null, null, null, emptyList()),
        0, 0, emptyList())

    private class FakeRepository(initial: GameState) : GameRepository {
        val state = MutableStateFlow<GameState?>(initial)
        var failWrite = false
        override fun observe() = state
        override suspend fun read() = state.value
        override suspend fun initializeIfAbsent(initial: GameState) = state.value ?: initial.also { state.value = it }
        override suspend fun update(transform: (GameState) -> GameState): GameState {
            val next = transform(checkNotNull(state.value))
            if (failWrite) throw java.io.IOException("disk")
            state.value = next
            return next
        }
    }
}
