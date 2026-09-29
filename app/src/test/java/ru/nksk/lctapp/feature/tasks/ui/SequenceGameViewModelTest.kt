package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.DeedGameScore

@OptIn(ExperimentalCoroutinesApi::class)
class SequenceGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextModel = 0

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun firstDemonstrationWaitsForBothLampVariantsAndReadinessDoesNotRestartIt() = runTest(dispatcher) {
        val game = model(saved(), artworkReady = false)
        advanceUntilIdle()
        assertEquals(SequencePhase.SHOWING, game.uiState.value.phase)
        assertNull(game.uiState.value.showingIndex)
        game.onAction(SequenceGameAction.ArtworkReady)
        runCurrent()
        assertEquals(0, game.uiState.value.showingIndex)
        advanceTimeBy(801)
        runCurrent()
        assertEquals(1, game.uiState.value.showingIndex)
        game.onAction(SequenceGameAction.ArtworkReady)
        runCurrent()
        assertEquals(1, game.uiState.value.showingIndex)
        advanceUntilIdle()
        assertEquals(SequencePhase.INPUT, game.uiState.value.phase)
    }

    @Test fun everyFastInputLightsItsLampWithoutBlockingTheRemainingSignals() = runTest(dispatcher) {
        val game = model(saved())
        advanceUntilIdle()
        game.onAction(SequenceGameAction.Tap(0))
        assertEquals(SequencePhase.INPUT, game.uiState.value.phase)
        assertEquals(setOf(0), game.uiState.value.inputSignals)
        assertEquals(1, game.uiState.value.game.position)
        game.onAction(SequenceGameAction.Tap(1))
        game.onAction(SequenceGameAction.Tap(2))
        assertEquals(1, game.uiState.value.game.correct)
        assertEquals(1, game.uiState.value.game.round)
        assertEquals(SequencePhase.FEEDBACK, game.uiState.value.phase)
        assertEquals(setOf(0, 1, 2), game.uiState.value.inputSignals)
        runCurrent()
        advanceTimeBy(221)
        runCurrent()
        assertTrue(game.uiState.value.inputSignals.isEmpty())
        assertEquals(SequencePhase.FEEDBACK, game.uiState.value.phase)
    }

    @Test fun repeatedLampExtendsItsPulseAndStillAcceptsBothInputs() = runTest(dispatcher) {
        val game = model(saved(intArrayOf(0, 0, 1)))
        advanceUntilIdle()
        game.onAction(SequenceGameAction.Tap(0))
        runCurrent()
        advanceTimeBy(150)
        game.onAction(SequenceGameAction.Tap(0))
        runCurrent()
        advanceTimeBy(71)
        runCurrent()
        assertEquals(2, game.uiState.value.game.position)
        assertEquals(setOf(0), game.uiState.value.inputSignals)
        advanceTimeBy(150)
        runCurrent()
        assertTrue(game.uiState.value.inputSignals.isEmpty())
        assertEquals(SequencePhase.INPUT, game.uiState.value.phase)
    }

    @Test fun wrongInputAlsoLightsItsLampAndNextRoundStartsWithoutOldPulses() = runTest(dispatcher) {
        val game = model(saved())
        advanceUntilIdle()
        game.onAction(SequenceGameAction.Tap(3))
        assertEquals(setOf(3), game.uiState.value.inputSignals)
        assertEquals(false, game.uiState.value.game.lastCorrect)
        val feedback = game.uiState.value
        game.onAction(SequenceGameAction.Tap(0))
        assertEquals(feedback, game.uiState.value)
        advanceUntilIdle()
        assertEquals(SequencePhase.INPUT, game.uiState.value.phase)
        assertEquals(1, game.uiState.value.game.round)
        assertEquals(4, game.uiState.value.game.requiredLength)
        assertTrue(game.uiState.value.inputSignals.isEmpty())
    }

    @Test fun restartCancelsInputPulseAndPendingRoundAdvance() = runTest(dispatcher) {
        val game = model(saved())
        advanceUntilIdle()
        game.onAction(SequenceGameAction.Tap(3))
        runCurrent()
        game.onAction(SequenceGameAction.Restart)
        assertTrue(game.uiState.value.inputSignals.isEmpty())
        advanceUntilIdle()
        assertEquals(SequencePhase.INPUT, game.uiState.value.phase)
        assertEquals(0, game.uiState.value.game.round)
        assertEquals(0, game.uiState.value.game.correct)
        assertEquals(3, game.uiState.value.game.roundLimit)
        assertEquals(3, game.uiState.value.game.requiredLength)
    }

    @Test fun newSessionRestoresThreeRoundsAndReplaysPartialInputWithoutAPersistedPulse() = runTest(dispatcher) {
        val state = SavedStateHandle()
        val game = model(state)
        assertEquals(3, state.get<Int>("round_limit"))
        advanceUntilIdle()
        game.onAction(SequenceGameAction.Tap(game.uiState.value.game.sequence.first()))
        val restored = model(copyOf(state))
        assertEquals(3, restored.uiState.value.game.roundLimit)
        assertEquals(game.uiState.value.game.sequence, restored.uiState.value.game.sequence)
        assertEquals(0, restored.uiState.value.game.position)
        assertTrue(restored.uiState.value.inputSignals.isEmpty())
        assertEquals(SequencePhase.SHOWING, restored.uiState.value.phase)
        advanceUntilIdle()
        assertEquals(SequencePhase.INPUT, restored.uiState.value.phase)
    }

    @Test fun legacySavedBoardWithoutLimitFinishesFiveRoundsAndRestoresItsResult() = runTest(dispatcher) {
        val state = saved(intArrayOf(0, 1, 2, 3, 0, 1, 2)).apply {
            remove<Int>("round_limit")
            this["round"] = 4
            this["correct"] = 3
            this["position"] = 2
        }
        val game = model(state)
        assertEquals(5, game.uiState.value.game.roundLimit)
        assertEquals(5, game.uiState.value.roundNumber)
        assertEquals(0, game.uiState.value.game.position)
        advanceUntilIdle()
        game.uiState.value.game.sequence.forEach { game.onAction(SequenceGameAction.Tap(it)) }
        val result = checkNotNull(DeedGameScore.fromSequence(game.uiState.value.game))
        assertEquals(5, result.attempts)
        assertEquals(4, result.correct)
        state.remove<Int>("round_limit")
        val restored = model(copyOf(state))
        assertTrue(restored.uiState.value.game.finished)
        assertEquals(game.uiState.value.game, restored.uiState.value.game)
        assertTrue(restored.uiState.value.inputSignals.isEmpty())
        assertEquals(5, checkNotNull(DeedGameScore.fromSequence(restored.uiState.value.game)).attempts)
        restored.onAction(SequenceGameAction.Restart)
        assertEquals(3, restored.uiState.value.game.roundLimit)
    }

    @Test fun invalidSavedBoardsCannotFinishOrExposeUnboundedSequences() {
        val invalid = listOf(
            saved(IntArray(1000)),
            saved().apply { this["round_limit"] = 4 },
            saved().apply { this["round"] = 3; this["last_correct"] = true },
            saved().apply { this["position"] = -1 },
            saved(intArrayOf(0, 1, 4)),
        )
        invalid.forEach { state ->
            val game = model(state).uiState.value.game
            assertTrue(game.isValid)
            assertEquals(0, game.round)
            assertEquals(3, game.sequence.size)
            assertEquals(3, game.roundLimit)
            assertNull(DeedGameScore.fromSequence(game))
        }
    }

    private fun model(saved: SavedStateHandle, artworkReady: Boolean = true) = SequenceGameViewModel(saved).also {
        store.put("sequence-${nextModel++}", it)
        if (artworkReady) it.onAction(SequenceGameAction.ArtworkReady)
    }

    private fun saved(sequence: IntArray = intArrayOf(0, 1, 2)) = SavedStateHandle(mapOf(
        "sequence" to sequence, "round" to 0, "correct" to 0, "position" to 0,
        "round_limit" to 3, "phase" to SequencePhase.INPUT.name,
    ))

    private fun copyOf(saved: SavedStateHandle) = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
}
