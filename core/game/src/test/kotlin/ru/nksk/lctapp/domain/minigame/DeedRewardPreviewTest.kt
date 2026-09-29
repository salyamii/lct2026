package ru.nksk.lctapp.domain.minigame

import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class DeedRewardPreviewTest {
    @Test fun newGamesStartAtTheAuthoredMaximumWithoutMakingThemPayable() {
        val memory = memoryBoard()
        val comparison = comparisonBoard()
        val precision = TargetStopState(zoneStart = 20)
        val previews = listOf(DeedRewardPreview.fromMemory(memory),
            DeedRewardPreview.fromComparison(comparison), DeedRewardPreview.fromPrecision(precision))

        for (preview in previews) {
            assertEquals(0, preview.mistakes)
            assertEquals(12L, preview.reward(12))
            assertEquals(1f, preview.fraction(12), 0f)
            assertEquals(0L, preview.reward(0))
            assertEquals(0f, preview.fraction(0), 0f)
        }
        assertEquals(memoryBoard(), memory)
        assertEquals(comparisonBoard(), comparison)
        assertEquals(TargetStopState(zoneStart = 20), precision)
        assertNull(DeedGameScore.fromMemory(memory))
        assertNull(DeedGameScore.fromComparison(comparison))
        assertNull(DeedGameScore.fromPrecision(precision))
    }

    @Test fun correctMemoryPairKeepsItsRewardBeforeAndAfterFeedback() {
        val firstCard = memoryBoard().tap(0)
        val pending = firstCard.tap(1)
        val resolved = pending.resolvePending()

        for (state in listOf(firstCard, pending, resolved)) {
            val preview = DeedRewardPreview.fromMemory(state)
            assertEquals(0, preview.mistakes)
            assertEquals(12L, preview.reward(12))
            assertNull(DeedGameScore.fromMemory(state))
        }
    }

    @Test fun missingAKnownMemoryPairReducesRewardOnceAndCorrectPairsDoNotRestoreLostCoins() {
        val discoveredMate = memoryBoard().tap(1).tap(2).resolvePending()
        val pending = discoveredMate.tap(0).tap(2)
        val resolved = pending.resolvePending()
        val correctPending = resolved.tap(0).tap(1)
        for (state in listOf(pending, resolved, correctPending, correctPending.resolvePending())) {
            val preview = DeedRewardPreview.fromMemory(state)
            assertEquals(1, preview.mistakes)
            assertEquals(10L, preview.reward(12))
        }
        val twiceWrong = resolved.tap(0).tap(2).resolvePending()
        assertEquals(2, DeedRewardPreview.fromMemory(twiceWrong).mistakes)
        assertEquals(9L, DeedRewardPreview.fromMemory(twiceWrong).reward(12))
    }

    @Test fun unknownMemoryPairsKeepTheirRewardBeforeAndAfterFeedback() {
        val pending = memoryBoard().tap(0).tap(2)
        val resolved = pending.resolvePending()
        for (state in listOf(pending, resolved, resolved.tap(0).tap(2), resolved.tap(0).tap(1))) {
            val preview = DeedRewardPreview.fromMemory(state)
            assertEquals(0, preview.mistakes)
            assertEquals(12L, preview.reward(12))
        }
    }

    @Test fun comparisonCountsTheAnsweredQuestionDuringFeedbackAndDoesNotCountItTwice() {
        val correct = comparisonBoard().answer(false)
        for (state in listOf(correct, correct.next())) {
            assertEquals(0, DeedRewardPreview.fromComparison(state).mistakes)
            assertEquals(12L, DeedRewardPreview.fromComparison(state).reward(12))
        }
        val wrong = correct.next().answer(true)
        val nextCorrect = wrong.next().answer(false)
        for (state in listOf(wrong, wrong.next(), nextCorrect, nextCorrect.next())) {
            assertEquals(1, DeedRewardPreview.fromComparison(state).mistakes)
            assertEquals(9L, DeedRewardPreview.fromComparison(state).reward(12))
            assertNull(DeedGameScore.fromComparison(state))
        }
    }

    @Test fun precisionCountsAMissOnceWhileHitsKeepTheRemainingReward() {
        val correct = TargetStopState(zoneStart = 20).stop(20)
        for (state in listOf(correct, correct.next())) {
            assertEquals(0, DeedRewardPreview.fromPrecision(state).mistakes)
            assertEquals(12L, DeedRewardPreview.fromPrecision(state).reward(12))
        }
        val wrong = correct.next().stop(0)
        val afterFeedback = wrong.next()
        val nextCorrect = afterFeedback.stop(afterFeedback.zoneStart)
        for (state in listOf(wrong, afterFeedback, nextCorrect, nextCorrect.next())) {
            assertEquals(1, DeedRewardPreview.fromPrecision(state).mistakes)
            assertEquals(9L, DeedRewardPreview.fromPrecision(state).reward(12))
            assertNull(DeedGameScore.fromPrecision(state))
        }
    }

    @Test fun finishedPreviewsEqualPayableScoresForEveryMechanicIncludingZeroAndMinimum() {
        for (mistakes in listOf(0, 1, 2, 40)) {
            var memory = memoryBoard().tap(1).tap(2).resolvePending()
            repeat(mistakes) { memory = memory.tap(0).tap(2).resolvePending() }
            repeat(MemoryState.PAIRS) { pair ->
                memory = memory.tap(pair * 2).tap(pair * 2 + 1).resolvePending()
            }
            assertTerminalReward(DeedRewardPreview.fromMemory(memory),
                checkNotNull(DeedGameScore.fromMemory(memory)))
        }
        for (correctCount in 0..PriceQuizState.QUESTION_COUNT) {
            var comparison = comparisonBoard()
            repeat(PriceQuizState.QUESTION_COUNT) { index ->
                comparison = comparison.answer(index >= correctCount).next()
            }
            assertTerminalReward(DeedRewardPreview.fromComparison(comparison),
                checkNotNull(DeedGameScore.fromComparison(comparison)))
        }
        for (hitCount in 0..TargetStopState.ROUNDS) {
            var precision = TargetStopState(zoneStart = 20)
            repeat(TargetStopState.ROUNDS) { index ->
                precision = precision.stop(if (index < hitCount) precision.zoneStart else 0).next()
            }
            assertTerminalReward(DeedRewardPreview.fromPrecision(precision),
                checkNotNull(DeedGameScore.fromPrecision(precision)))
        }
    }

    @Test fun projectionRoundsDownWithoutOverflowAndKeepsTheOneCoinFloorVisible() {
        val threePossible = comparisonBoard().answer(true).next().answer(true)
        val preview = DeedRewardPreview.fromComparison(threePossible)
        val expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3))
            .divide(BigInteger.valueOf(5)).toLong()
        assertEquals(expected, preview.reward(Long.MAX_VALUE))
        assertEquals(4L, preview.reward(8))
        assertEquals(0.5f, preview.fraction(8), 0f)

        var allWrong = comparisonBoard()
        repeat(PriceQuizState.QUESTION_COUNT) { allWrong = allWrong.answer(true).next() }
        val minimum = DeedRewardPreview.fromComparison(allWrong)
        assertEquals(1L, minimum.reward(4))
        assertEquals(0.25f, minimum.fraction(4), 0f)
        assertEquals(1L, minimum.reward(1))
        assertEquals(1f, minimum.fraction(1), 0f)
        assertEquals(0L, minimum.reward(0))
    }

    private fun assertTerminalReward(preview: DeedRewardPreview, score: DeedGameScore) {
        for (maximum in listOf(0L, 1L, 4L, 8L, 12L, Long.MAX_VALUE)) {
            assertEquals(score.reward(maximum), preview.reward(maximum))
        }
    }

    private fun memoryBoard() = MemoryState(List(MemoryState.PAIRS * 2) { it / 2 })
    private fun comparisonBoard() = PriceQuizState(List(PriceQuizState.QUESTION_COUNT) { QuizQuestion(10, 20) })
}
