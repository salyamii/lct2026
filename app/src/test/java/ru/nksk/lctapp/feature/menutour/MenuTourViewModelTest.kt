package ru.nksk.lctapp.feature.menutour

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.domain.tutorial.MenuTourPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class MenuTourViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun rapidNextDoesNotSkipStepsAndRecreationRestoresTheLastSavedStep() = runTest(dispatcher) {
        val preferences = MemoryPreferences()
        val model = MenuTourViewModel(preferences)
        model.prepare(newPlayer = true)
        advanceUntilIdle()
        model.next()
        model.next()
        assertEquals(0, model.uiState.value.step)
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.step)
        val restored = MenuTourViewModel(preferences)
        restored.prepare(newPlayer = false)
        advanceUntilIdle()
        assertEquals(1, restored.uiState.value.step)
    }

    @Test fun skipSuppressesAllHintsAndExplicitReplayStartsOver() = runTest(dispatcher) {
        val model = MenuTourViewModel(MemoryPreferences(6))
        model.prepare(false)
        advanceUntilIdle()
        model.skip()
        advanceUntilIdle()
        assertEquals(11, model.uiState.value.step)
        model.restart()
        advanceUntilIdle()
        assertEquals(0, model.uiState.value.step)
        model.previous()
        advanceUntilIdle()
        assertEquals(0, model.uiState.value.step)
    }

    @Test fun failedWriteKeepsTheCurrentStepAndRetrySavesTheSameNextStep() = runTest(dispatcher) {
        val preferences = MemoryPreferences(8)
        val model = MenuTourViewModel(preferences)
        model.prepare(false)
        advanceUntilIdle()
        preferences.failWrites = true
        model.next()
        advanceUntilIdle()
        assertEquals(8, model.uiState.value.step)
        assertTrue(model.uiState.value.error)
        preferences.failWrites = false
        model.retry()
        advanceUntilIdle()
        assertEquals(9, model.uiState.value.step)
        assertFalse(model.uiState.value.error)
    }

    @Test fun readFailureDoesNotBecomeANewTourAndCanBeDismissedWithoutWriting() = runTest(dispatcher) {
        val preferences = MemoryPreferences(5).apply { failReads = true }
        val model = MenuTourViewModel(preferences)
        model.prepare(true)
        advanceUntilIdle()
        assertTrue(model.uiState.value.error)
        assertNull(model.uiState.value.step)
        model.dismissError()
        model.prepare(true)
        advanceUntilIdle()
        assertTrue(model.uiState.value.dismissedForSession)
        assertEquals(5, preferences.step)
    }

    private class MemoryPreferences(var step: Int? = null) : MenuTourPreferences {
        var failReads = false
        var failWrites = false
        override suspend fun readStep(): Int? { if (failReads) throw IOException(); return step }
        override suspend fun saveStep(step: Int) { if (failWrites) throw IOException(); this.step = step }
    }
}
