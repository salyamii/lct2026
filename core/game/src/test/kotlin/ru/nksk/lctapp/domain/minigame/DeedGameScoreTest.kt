package ru.nksk.lctapp.domain.minigame

import java.math.BigInteger
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
        var board = MemoryState(List(MemoryState.PAIRS * 2) { it / 2 }).tap(1).tap(2).resolvePending()
        repeat(2) { board = board.tap(0).tap(2).resolvePending() }
        repeat(MemoryState.PAIRS) { pair -> board = board.tap(pair * 2).tap(pair * 2 + 1).resolvePending() }
        val score = checkNotNull(DeedGameScore.fromMemory(board))
        assertEquals(8, score.correct)
        assertEquals(10, score.attempts)
        assertEquals(11, board.moves)
        assertEquals(8L, score.reward(10))
    }

    @Test fun completedComparisonsHaveAMinimumEvenWithNoCorrectAnswers() {
        for (correct in listOf(0, 1)) {
            val board = PriceQuizState.create().copy(current = PriceQuizState.QUESTION_COUNT, correctAnswers = correct)
            val score = checkNotNull(DeedGameScore.fromComparison(board))
            assertEquals(1L, score.reward(4))
            assertEquals(1L, score.reward(1))
            assertEquals(0L, score.reward(0))
        }
    }

    @Test fun findingAllPairsStillPaysOneCoinAfterManyMistakes() {
        var board = MemoryState(List(MemoryState.PAIRS * 2) { it / 2 }).tap(1).tap(2).resolvePending()
        repeat(40) { board = board.tap(0).tap(2).resolvePending() }
        repeat(MemoryState.PAIRS) { pair -> board = board.tap(pair * 2).tap(pair * 2 + 1).resolvePending() }
        val score = checkNotNull(DeedGameScore.fromMemory(board))
        assertEquals(1L, score.reward(4))
        assertEquals(0L, score.reward(0))
    }

    @Test fun precisionPaysAtLeastOneCoinAndPreservesTheFullMaximum() {
        for (hit in listOf(false, true)) {
            var board = TargetStopState.create()
            repeat(TargetStopState.ROUNDS) { board = board.stop(if (hit) board.zoneStart else 0).next() }
            val score = checkNotNull(DeedGameScore.fromPrecision(board))
            assertEquals(if (hit) 6L else 1L, score.reward(6))
            assertEquals(0L, score.reward(0))
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

    @Test fun exploratoryMemoryMovesDoNotReduceTheCompletedReward() {
        var board = MemoryState(List(MemoryState.PAIRS * 2) { it / 2 })
        repeat(5) { board = board.tap(0).tap(2).resolvePending() }
        repeat(MemoryState.PAIRS) { pair -> board = board.tap(pair * 2).tap(pair * 2 + 1).resolvePending() }
        val score = checkNotNull(DeedGameScore.fromMemory(board))
        assertEquals(13, board.moves)
        assertEquals(0, board.recallMistakes)
        assertEquals(8, score.attempts)
        assertEquals(12L, score.reward(12))
    }

    @Test fun completedMemoryRejectsImpossibleRecallCounts() {
        var board = MemoryState(List(MemoryState.PAIRS * 2) { it / 2 })
        repeat(MemoryState.PAIRS) { pair -> board = board.tap(pair * 2).tap(pair * 2 + 1).resolvePending() }
        assertNotNull(DeedGameScore.fromMemory(board))
        assertNull(DeedGameScore.fromMemory(board.copy(recallMistakes = -1)))
        assertNull(DeedGameScore.fromMemory(board.copy(recallMistakes = 1)))
    }

    @Test fun storedMemoryScoresKeepTheirExistingShapeAndRecordedReward() {
        val recorded = Json.decodeFromString<DeedGameScore>(
            """{"kind":"MEMORY","correct":8,"attempts":10}""",
        )
        assertEquals(8L, recorded.reward(10))
        val encoded = Json.parseToJsonElement(Json.encodeToString(recorded)).jsonObject
        assertEquals(setOf("kind", "correct", "attempts"), encoded.keys)
    }
}
