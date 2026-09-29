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

    @Test fun correctMidSequenceTapKeepsTheRoundOpen() {
        // Раунд завершается только на последнем сигнале: до него lastCorrect остаётся null,
        // иначе UI показывал бы «сбой» после первого же верного сигнала.
        val board = SequenceState(listOf(0, 1, 2))
        val first = board.tap(0)
        assertNull(first.lastCorrect)
        assertEquals(0, first.round)
        assertEquals(1, first.position)
        val second = first.tap(1)
        assertNull(second.lastCorrect)
        assertEquals(2, second.position)
        val last = second.tap(2)
        assertTrue(last.lastCorrect == true)
        assertEquals(1, last.round)
    }

    @Test fun tapsAreIgnoredUntilNextRoundStarts() {
        val board = SequenceState(listOf(0, 1, 2)).tap(0).tap(1).tap(2)
        assertEquals(board, board.tap(0))
        val next = board.next(fixed)
        // В новом раунде последовательность на одну вспышку длиннее.
        assertEquals(4, next.sequence.size)
        assertNull(next.lastCorrect)
        assertEquals(board.correct, next.correct)
    }

    @Test fun newGameFinishesAfterThreeCombinationsOfThreeFourAndFiveSignals() {
        var board = SequenceState.create(fixed)
        assertEquals(3, board.roundLimit)
        repeat(SequenceState.ROUNDS) {
            assertEquals(3 + it, board.requiredLength)
            assertTrue(board.isValid)
            assertFalse(board.finished)
            board = board.playRound()
        }
        assertTrue(board.finished)
        assertEquals(SequenceState.ROUNDS, board.round)
        assertEquals(SequenceState.ROUNDS, board.correct)
        assertTrue(board.isValid)
        assertEquals(board, board.tap(0).next(fixed))
    }

    @Test fun restoredLegacySessionStillCompletesAllFiveCombinations() {
        var board = SequenceState.create(fixed).copy(roundLimit = SequenceState.LEGACY_ROUNDS)
        repeat(5) { round ->
            assertFalse(board.finished)
            assertTrue(board.isValid)
            assertEquals(3 + round, board.requiredLength)
            board = board.playRound()
        }
        assertTrue(board.finished)
        assertTrue(board.isValid)
        assertEquals(5, board.correct)
        assertEquals(7, board.requiredLength)
    }

    @Test fun invalidSessionBoundsCannotBecomeCompletedScores() {
        val completed = (0 until 3).fold(SequenceState.create(fixed)) { board, _ -> board.playRound() }
        for (invalid in listOf(
            completed.copy(roundLimit = 4), completed.copy(round = 4), completed.copy(position = 1),
            completed.copy(sequence = listOf(0, 1, 2)), completed.copy(sequence = List(5) { 4 }),
            completed.copy(lastCorrect = null), completed.copy(correct = 0),
        )) {
            assertFalse(invalid.isValid)
            assertNull(DeedGameScore.fromSequence(invalid))
        }
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
