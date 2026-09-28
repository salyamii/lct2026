package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
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
import ru.nksk.lctapp.domain.minigame.PipesState
import ru.nksk.lctapp.domain.minigame.StackingState

@OptIn(ExperimentalCoroutinesApi::class)
class ExtendedMiniGameRestorationTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun stackingPersistsDropsCompletionAndRestartInsteadOfOnlyUpdatingTheFlow() {
        val saved = SavedStateHandle()
        val model = StackingGameViewModel(saved).also { store.put("original", it) }
        model.onAction(StackingGameAction.Drop)
        val restored = StackingGameViewModel(copyOf(saved)).also { store.put("restored", it) }
        assertEquals(model.uiState.value, restored.uiState.value)
        assertEquals(1, restored.uiState.value.game.placed)
        repeat(StackingState.ROUNDS - 1) { model.onAction(StackingGameAction.Drop) }
        val finished = StackingGameViewModel(copyOf(saved)).also { store.put("finished", it) }
        assertTrue(finished.uiState.value.game.won)
        model.onAction(StackingGameAction.Restart)
        val restarted = StackingGameViewModel(copyOf(saved)).also { store.put("restarted", it) }
        assertEquals(StackingState.create(), restarted.uiState.value.game)
    }

    @Test fun sequenceRestoredDuringFeedbackContinuesOnceAndShowsTheNextRound() = runTest(dispatcher) {
        val model = SequenceGameViewModel(SavedStateHandle(mapOf(
            "sequence" to intArrayOf(0, 1, 2), "round" to 1, "correct" to 1,
            "position" to 0, "last_correct" to true, "phase" to SequencePhase.FEEDBACK.name,
        ))).also { store.put("sequence", it) }
        advanceUntilIdle()
        assertEquals(SequencePhase.INPUT, model.uiState.value.phase)
        assertEquals(1, model.uiState.value.game.round)
        assertEquals(1, model.uiState.value.game.correct)
        assertEquals(4, model.uiState.value.game.requiredLength)
        assertNull(model.uiState.value.game.lastCorrect)
    }

    @Test fun pipesRestoresCompletedPathsAndAnActiveRouteFromArrayAndLegacyListStorage() {
        val saved = SavedStateHandle(mapOf(
            "endpoint_colors" to PipesState.PUZZLE.map { it.color }.toIntArray(),
            "endpoint_firsts" to PipesState.PUZZLE.map { it.first }.toIntArray(),
            "endpoint_seconds" to PipesState.PUZZLE.map { it.second }.toIntArray(),
        ))
        val model = PipesGameViewModel(saved)
        for (cell in listOf(0, 5, 10, 15, 20, 4, 9)) model.onAction(PipesGameAction.Press(cell))
        assertEquals(1, model.uiState.value.game.paths.size)
        assertEquals(listOf(4, 9), model.uiState.value.game.activePath)
        assertEquals(model.uiState.value, PipesGameViewModel(copyOf(saved)).uiState.value)
        val legacy = copyOf(saved)
        legacy["path_cells"] = model.uiState.value.game.paths.values.map { it.toIntArray() }
        assertEquals(model.uiState.value, PipesGameViewModel(legacy).uiState.value)
    }

    private fun copyOf(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
}
