package ru.nksk.lctapp.domain.minigame

import org.junit.Assert.*
import org.junit.Test

class ExtendedDeedRewardPreviewTest {
    @Test fun newBoardsShowTheMaximumWithoutProducingPayableResults() {
        val lights = LightsState.create()
        val sequence = SequenceState.create()
        val pipes = PipesState.create()
        val differences = DifferencesState.create()
        val stacking = StackingState.create()
        val previews = listOf(DeedRewardPreview.fromLights(lights), DeedRewardPreview.fromSequence(sequence),
            DeedRewardPreview.fromPipes(pipes), DeedRewardPreview.fromDifferences(differences),
            DeedRewardPreview.fromStacking(stacking))
        previews.forEach {
            assertEquals(0, it.mistakes)
            assertEquals(12L, it.reward(12))
            assertEquals(1f, it.fraction(12), 0f)
            assertEquals(0L, it.reward(0))
        }
        assertNull(DeedGameScore.fromLights(lights))
        assertNull(DeedGameScore.fromSequence(sequence))
        assertNull(DeedGameScore.fromPipes(pipes))
        assertNull(DeedGameScore.fromDifferences(differences))
        assertNull(DeedGameScore.fromStacking(stacking))
    }

    @Test fun sequenceMissCountsOnceAndCorrectSignalsKeepTheRemainingReward() {
        val start = SequenceState(listOf(0, 1, 2))
        val wrong = start.tap(1)
        val next = wrong.next()
        val partial = next.tap(next.sequence.first())
        for (board in listOf(wrong, wrong.tap(1), next, partial)) {
            assertEquals(1, DeedRewardPreview.fromSequence(board).mistakes)
            assertEquals(8L, DeedRewardPreview.fromSequence(board).reward(12))
            assertNull(DeedGameScore.fromSequence(board))
        }
        for (correctRounds in 0..SequenceState.ROUNDS) {
            var board = start
            repeat(SequenceState.ROUNDS) { round ->
                board = if (round < correctRounds) board.sequence.fold(board) { game, signal -> game.tap(signal) }
                else board.tap((board.sequence.first() + 1) % SequenceState.SIGNAL_COUNT)
                board = board.next()
            }
            assertFinal(DeedRewardPreview.fromSequence(board), checkNotNull(DeedGameScore.fromSequence(board)))
        }
    }

    @Test fun legacySequencePreviewAndPaymentKeepTheirFiveRoundDenominator() {
        var board = SequenceState.create().copy(roundLimit = SequenceState.LEGACY_ROUNDS)
        board = board.tap((board.sequence.first() + 1) % SequenceState.SIGNAL_COUNT)
        assertEquals(9L, DeedRewardPreview.fromSequence(board).reward(12))
        repeat(4) {
            board = board.next()
            board = board.sequence.fold(board) { game, signal -> game.tap(signal) }
        }
        val score = checkNotNull(DeedGameScore.fromSequence(board))
        assertEquals(5, score.attempts)
        assertEquals(4, score.correct)
        assertFinal(DeedRewardPreview.fromSequence(board), score)
    }

    @Test fun differencesChargeMissesAndKeepPreviewEqualToTheFinalScore() {
        for (misses in listOf(0, 1, 2, 50)) {
            var board = DifferencesState.create()
            val unchangedCell = board.top.indices.first { it !in board.differences }
            repeat(misses) { board = board.tap(unchangedCell) }
            val afterErrors = DeedRewardPreview.fromDifferences(board).reward(12)
            board.differences.forEach { cell ->
                board = board.tap(cell)
                assertEquals(misses, DeedRewardPreview.fromDifferences(board).mistakes)
                assertEquals(afterErrors, DeedRewardPreview.fromDifferences(board).reward(12))
            }
            assertFinal(DeedRewardPreview.fromDifferences(board), checkNotNull(DeedGameScore.fromDifferences(board)))
        }
    }

    @Test fun stackingPreservesItsMaximumUntilTheMissThatEndsTheGame() {
        for (placed in 1..StackingState.ROUNDS) {
            var board = StackingState.create()
            repeat(placed) {
                board = board.dropAt(0)
                assertEquals(12L, DeedRewardPreview.fromStacking(board).reward(12))
                if (!board.finished) assertNull(DeedGameScore.fromStacking(board))
            }
            if (!board.finished) board = board.dropAt(StackingState.SPACE - board.currentWidth)
            assertTrue(board.finished)
            assertFinal(DeedRewardPreview.fromStacking(board), checkNotNull(DeedGameScore.fromStacking(board)))
        }
    }

    @Test fun oneShotPuzzlesAllowExplorationAndPayOnlyAfterTheCompleteSolution() {
        var lights = LightsState.create().tap(1).tap(1)
        assertEquals(12L, DeedRewardPreview.fromLights(lights).reward(12))
        assertNull(DeedGameScore.fromLights(lights))
        for (cell in listOf(0, 4, 12, 20, 24)) lights = lights.tap(cell)
        assertFinal(DeedRewardPreview.fromLights(lights), checkNotNull(DeedGameScore.fromLights(lights)))

        for (reverse in listOf(false, true)) {
            var pipes = PipesState(PipesState.PUZZLE).press(0).press(5).release()
            assertEquals(12L, DeedRewardPreview.fromPipes(pipes).reward(12))
            assertNull(DeedGameScore.fromPipes(pipes))
            for (path in solvedPaths) {
                for (cell in if (reverse) path.reversed() else path) pipes = pipes.press(cell)
            }
            assertTrue(pipes.won)
            assertFinal(DeedRewardPreview.fromPipes(pipes), checkNotNull(DeedGameScore.fromPipes(pipes)))
        }
    }

    private fun assertFinal(preview: DeedRewardPreview, score: DeedGameScore) {
        for (maximum in listOf(0L, 1L, 4L, 8L, 12L, Long.MAX_VALUE)) {
            assertEquals(score.reward(maximum), preview.reward(maximum))
            assertTrue(preview.reward(maximum) in (if (maximum > 0L) 1L else 0L)..maximum)
        }
    }

    private val solvedPaths = listOf(listOf(0, 5, 10, 15, 20), listOf(4, 9, 14, 19, 24), listOf(11, 6, 7, 8, 13))
}
