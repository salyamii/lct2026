package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
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
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.QuizQuestion

@OptIn(ExperimentalCoroutinesApi::class)
class MiniGameViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun quizHighlightsTheLargerAmountOnEitherSide() {
        val left = PriceQuizUiState(PriceQuizState(listOf(QuizQuestion(80, 20))).answer(true))
        assertTrue(left.leftIsAnswer)
        assertFalse(left.rightIsAnswer)
        val right = PriceQuizUiState(PriceQuizState(listOf(QuizQuestion(20, 80))).answer(true))
        assertFalse(right.leftIsAnswer)
        assertTrue(right.rightIsAnswer)
        val before = PriceQuizUiState(PriceQuizState(listOf(QuizQuestion(80, 20))))
        assertFalse(before.leftIsAnswer)
        assertFalse(before.rightIsAnswer)
    }

    @Test fun memoryRestoresOpenCardsAndResolvesThePendingPairOnce() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("faces" to intArrayOf(0, 0, 1, 1)))
        val model = MemoryGameViewModel(saved)
        model.onAction(MemoryGameAction.Tap(0))
        model.onAction(MemoryGameAction.Tap(1))
        model.onAction(MemoryGameAction.Tap(2)) // Ignore input while feedback is visible.
        val restored = MemoryGameViewModel(copyOf(saved))
        assertEquals(setOf(0, 1), restored.uiState.value.game.faceUp)
        assertEquals(1, restored.uiState.value.game.moves)
        advanceUntilIdle()
        assertEquals(setOf(0, 1), restored.uiState.value.game.matched)
        assertNull(restored.uiState.value.game.pending)
        assertEquals(1, restored.uiState.value.game.moves)
    }

    @Test fun memoryRestartCancelsPendingFeedback() = runTest(dispatcher) {
        val model = MemoryGameViewModel(SavedStateHandle(mapOf("faces" to intArrayOf(0, 0))))
        model.onAction(MemoryGameAction.Tap(0))
        model.onAction(MemoryGameAction.Tap(1))
        model.onAction(MemoryGameAction.Restart)
        val fresh = model.uiState.value
        advanceUntilIdle()
        assertEquals(fresh, model.uiState.value)
        assertEquals(0, fresh.game.moves)
        assertFalse(fresh.game.won)
        assertTrue(fresh.game.seen.isEmpty())
        assertEquals(0, fresh.game.recallMistakes)
    }

    @Test fun memoryRestoresKnowledgeAndCountsAMissedKnownPairOnlyOnce() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("faces" to intArrayOf(0, 0, 1, 1, 2, 2)))
        val model = MemoryGameViewModel(saved)
        model.onAction(MemoryGameAction.Tap(0))
        model.onAction(MemoryGameAction.Tap(2))
        advanceUntilIdle()
        assertEquals(0, model.uiState.value.game.recallMistakes)
        model.onAction(MemoryGameAction.Tap(1))

        val restoredSave = copyOf(saved)
        val restored = MemoryGameViewModel(restoredSave)
        assertEquals(setOf(0, 1, 2), restored.uiState.value.game.seen)
        restored.onAction(MemoryGameAction.Tap(4)) // The matching card at 0 was already shown.
        assertEquals(1, restored.uiState.value.game.recallMistakes)
        val pendingRestored = MemoryGameViewModel(copyOf(restoredSave))
        assertEquals(1, pendingRestored.uiState.value.game.recallMistakes)
        advanceUntilIdle()
        assertEquals(1, pendingRestored.uiState.value.game.recallMistakes)
        assertEquals(setOf(0, 1, 2, 4), pendingRestored.uiState.value.game.seen)
        pendingRestored.onAction(MemoryGameAction.Restart)
        assertTrue(pendingRestored.uiState.value.game.seen.isEmpty())
        assertEquals(0, pendingRestored.uiState.value.game.recallMistakes)
    }

    @Test fun oldMemorySaveDoesNotInventKnowledgeOrPenaltiesFromMoves() = runTest(dispatcher) {
        val restored = MemoryGameViewModel(SavedStateHandle(mapOf(
            "faces" to intArrayOf(0, 0, 1, 1), "matched" to intArrayOf(0, 1),
            "face_up" to intArrayOf(2), "moves" to 6,
        )))
        assertEquals(setOf(0, 1, 2), restored.uiState.value.game.seen)
        assertEquals(6, restored.uiState.value.game.moves)
        assertEquals(0, restored.uiState.value.game.recallMistakes)
    }

    @Test fun quizRestoresFeedbackAndWaitsForExplicitNextWithoutCountingRepeatedTaps() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("questions" to intArrayOf(80, 20, 15, 60)))
        val model = PriceQuizViewModel(saved)
        model.onAction(PriceQuizAction.Next(0))
        assertEquals(0, model.uiState.value.game.current)
        model.onAction(PriceQuizAction.Answer(true))
        model.onAction(PriceQuizAction.Answer(true))
        val restored = PriceQuizViewModel(copyOf(saved))
        assertTrue(restored.uiState.value.leftIsAnswer)
        assertEquals(2, restored.uiState.value.game.reward)
        advanceUntilIdle()
        assertEquals(0, restored.uiState.value.game.current)
        assertEquals(true, restored.uiState.value.game.lastCorrect)
        restored.onAction(PriceQuizAction.Next(0))
        assertEquals(1, restored.uiState.value.game.current)
        assertNull(restored.uiState.value.game.lastCorrect)
        restored.onAction(PriceQuizAction.Answer(false))
        restored.onAction(PriceQuizAction.Next(0)) // A delayed click from the previous question cannot advance.
        advanceUntilIdle()
        assertEquals(1, restored.uiState.value.game.current)
        assertEquals(true, restored.uiState.value.game.lastCorrect)
        assertFalse(restored.uiState.value.game.finished)
        restored.onAction(PriceQuizAction.Next(1))
        restored.onAction(PriceQuizAction.Next(1))
        assertTrue(restored.uiState.value.game.finished)
        assertEquals(4, restored.uiState.value.game.reward)
        assertFalse(restored.uiState.value.rightIsAnswer)
    }

    @Test fun quizRestartClearsFeedbackAndCannotSkipTheNewQuestion() = runTest(dispatcher) {
        val model = PriceQuizViewModel(SavedStateHandle())
        model.onAction(PriceQuizAction.Answer(true))
        model.onAction(PriceQuizAction.Restart)
        val fresh = model.uiState.value
        model.onAction(PriceQuizAction.Next(0))
        advanceUntilIdle()
        assertEquals(fresh, model.uiState.value)
        assertEquals(0, fresh.game.current)
        assertEquals(0, fresh.game.reward)
    }

    @Test fun telescopeRestoresStoppedMarkerAndZoneUntilFeedbackEnds() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("zone" to 40))
        val model = TargetStopViewModel(saved)
        model.onAction(TargetStopAction.Stop(0.5f))
        model.onAction(TargetStopAction.Stop(0.5f))
        val restored = TargetStopViewModel(copyOf(saved))
        assertEquals(1, restored.uiState.value.roundNumber)
        assertEquals(40, restored.uiState.value.game.zoneStart)
        assertEquals(0.5f, restored.uiState.value.stoppedPosition)
        assertEquals(1, restored.uiState.value.game.hits)
        advanceUntilIdle()
        assertNull(restored.uiState.value.stoppedPosition)
        assertNull(restored.uiState.value.game.lastHit)
        assertEquals(1, restored.uiState.value.game.round)
        assertEquals(2, restored.uiState.value.roundNumber)
    }

    @Test fun telescopeRestartCancelsPreviousFeedbackAndRejectsInvalidInput() = runTest(dispatcher) {
        val model = TargetStopViewModel(SavedStateHandle(mapOf("zone" to 40)))
        model.onAction(TargetStopAction.Stop(Float.NaN))
        assertEquals(0, model.uiState.value.game.round)
        model.onAction(TargetStopAction.Stop(0.5f))
        model.onAction(TargetStopAction.Restart)
        val fresh = model.uiState.value
        advanceUntilIdle()
        assertEquals(fresh, model.uiState.value)
        assertEquals(0, fresh.game.hits)
    }

    @Test fun completedTelescopeRestoresWithoutStartingAnotherRound() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("zone" to 40, "round" to 4, "hits" to 4))
        val model = TargetStopViewModel(saved)
        model.onAction(TargetStopAction.Stop(0.5f))
        val restored = TargetStopViewModel(copyOf(saved))
        advanceUntilIdle()
        assertTrue(restored.uiState.value.game.finished)
        assertEquals(10, restored.uiState.value.game.reward)
        assertEquals(40, restored.uiState.value.game.zoneStart)
    }

    private fun copyOf(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
}
