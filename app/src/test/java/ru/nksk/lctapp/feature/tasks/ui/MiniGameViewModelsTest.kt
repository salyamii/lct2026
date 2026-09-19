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
    }

    @Test fun quizRestoresAnswerAndAdvancesWithoutCountingRepeatedTaps() = runTest(dispatcher) {
        val saved = SavedStateHandle(mapOf("questions" to intArrayOf(80, 20, 15, 60)))
        val model = PriceQuizViewModel(saved)
        model.onAction(PriceQuizAction.Answer(true))
        model.onAction(PriceQuizAction.Answer(true))
        val restored = PriceQuizViewModel(copyOf(saved))
        assertTrue(restored.uiState.value.leftIsAnswer)
        assertEquals(2, restored.uiState.value.game.reward)
        advanceUntilIdle()
        assertEquals(1, restored.uiState.value.game.current)
        assertNull(restored.uiState.value.game.lastCorrect)
        restored.onAction(PriceQuizAction.Answer(false))
        advanceUntilIdle()
        assertTrue(restored.uiState.value.game.finished)
        assertEquals(4, restored.uiState.value.game.reward)
        assertFalse(restored.uiState.value.rightIsAnswer)
    }

    @Test fun quizRestartCancelsThePreviousQuestionsFeedback() = runTest(dispatcher) {
        val model = PriceQuizViewModel(SavedStateHandle())
        model.onAction(PriceQuizAction.Answer(true))
        model.onAction(PriceQuizAction.Restart)
        val fresh = model.uiState.value
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
