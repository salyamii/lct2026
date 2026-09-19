package ru.nksk.lctapp.domain.minigame

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class DeedGameScoreTest {
    @Test fun comparisonPaysForTheCorrectShareAndRoundsDown() {
        var board = PriceQuizState.create()
        repeat(PriceQuizState.QUESTION_COUNT) { index ->
            board = board.answer(if (index < 3) board.question.leftIsBigger else !board.question.leftIsBigger).next()
        }
        val score = checkNotNull(DeedGameScore.fromComparison(board))
        assertEquals(4L, score.reward(8))
        assertEquals(0L, score.reward(0))
        val expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)).divide(BigInteger.valueOf(5))
        assertEquals(expected.toLong(), score.reward(Long.MAX_VALUE))
    }

    @Test fun memoryMistakesReduceTheRewardDespiteAllPairsEventuallyBeingFound() {
        var board = MemoryState(List(MemoryState.PAIRS * 2) { it / 2 })
        repeat(2) { board = board.tap(0).tap(2).resolvePending() }
        repeat(MemoryState.PAIRS) { pair -> board = board.tap(pair * 2).tap(pair * 2 + 1).resolvePending() }
        val score = checkNotNull(DeedGameScore.fromMemory(board))
        assertEquals(8, score.correct)
        assertEquals(10, score.attempts)
        assertEquals(8L, score.reward(10))
    }

    @Test fun precisionCanPayZeroOrTheFullMaximum() {
        for (hit in listOf(false, true)) {
            var board = TargetStopState.create()
            repeat(TargetStopState.ROUNDS) { board = board.stop(if (hit) board.zoneStart else 0).next() }
            val score = checkNotNull(DeedGameScore.fromPrecision(board))
            assertEquals(if (hit) 6L else 0L, score.reward(6))
        }
    }

    @Test fun incompleteOrMalformedBoardsDoNotProducePayableResults() {
        assertNull(DeedGameScore.fromMemory(MemoryState.deal()))
        assertNull(DeedGameScore.fromMemory(MemoryState(emptyList())))
        assertNull(DeedGameScore.fromComparison(PriceQuizState.create()))
        assertNull(DeedGameScore.fromComparison(PriceQuizState(emptyList(), current = 5, correctAnswers = 5)))
        assertNull(DeedGameScore.fromPrecision(TargetStopState.create()))
        assertNull(DeedGameScore.fromPrecision(TargetStopState(10, round = 5, hits = 6, lastHit = true)))
    }
}
