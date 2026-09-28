package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SequenceStateTest {
    private val fixed = Random(7)

    @Test fun correctRepetitionCompletesTheRound() {
        val board = SequenceState(listOf(0, 1, 2))
        val played = board.tap(0).tap(1).tap(2)
        assertEquals(1, played.round)
        assertEquals(1, played.correct)
        assertTrue(played.lastCorrect == true)
        assertEquals(0, played.position)
    }

    @Test fun mistakeCompletesTheRoundWithoutAPoint() {
        val board = SequenceState(listOf(0, 1, 2))
        val played = board.tap(0).tap(3)
        assertEquals(1, played.round)
        assertEquals(0, played.correct)
        assertTrue(played.lastCorrect == false)
    }

    @Test fun tapsAreIgnoredUntilNextRoundStarts() {
        val board = SequenceState(listOf(0, 1)).tap(0).tap(1)
        assertEquals(board, board.tap(0))
        val next = board.next(fixed)
        // В новом раунде последовательность на одну вспышку длиннее.
        assertEquals(4, next.sequence.size)
        assertNull(next.lastCorrect)
        assertEquals(board.correct, next.correct)
    }

    @Test fun fullGameFinishesAfterFiveRounds() {
        var board = SequenceState.create(fixed)
        repeat(SequenceState.ROUNDS) {
            board = board.playRound()
        }
        assertTrue(board.finished)
        assertEquals(SequenceState.ROUNDS, board.round)
        assertEquals(SequenceState.ROUNDS, board.correct)
    }

    @Test fun createDealsTheShortestFirstRound() {
        val board = SequenceState.create(fixed)
        assertEquals(SequenceState.FIRST_ROUND_LENGTH, board.sequence.size)
        assertEquals(0, board.round)
    }

    private fun SequenceState.playRound(): SequenceState {
        val played = sequence.fold(this) { state, signal -> state.tap(signal) }
        return played.next(fixed)
    }
}
