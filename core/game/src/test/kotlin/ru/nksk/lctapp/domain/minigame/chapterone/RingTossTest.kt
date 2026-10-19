package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.DeedGameKind
import ru.nksk.lctapp.domain.minigame.DeedGameScore

class RingTossTest {
    @Test fun backwardPullDeterminesTheLandingWithoutRandomness() {
        val game = RingToss().pull(0f, .188f)
        assertEquals(1, game.throws.size)
        assertEquals(1, game.hits)
        assertEquals(1, game.throws.single().pegIndex)
        assertEquals(game, RingToss().pull(0f, .188f))
        assertEquals(0, RingToss().pull(.20f, .10f).hits)
    }

    @Test fun shortWrongDirectionAndNonFinitePullsDoNotSpendRings() {
        val game = RingToss().throwAtPeg(0)
        for ((dx, dy) in listOf(0f to .02f, 0f to -.2f, Float.NaN to .2f,
            0f to Float.POSITIVE_INFINITY)) assertEquals(game, game.pull(dx, dy))
        assertEquals(game, game.throwAtPeg(-1))
        assertEquals(game, game.throwAtPeg(3))
    }

    @Test fun fiveMissesCompleteAndFurtherGesturesCannotChangeTheResult() {
        var game = RingToss()
        repeat(RingToss.THROW_COUNT) {
            assertFalse(game.complete)
            game = game.pull(.20f, .10f)
        }
        assertTrue(game.complete)
        assertEquals(0, game.hits)
        assertEquals(game, game.throwAtPeg(1))
        assertEquals(game, game.pull(0f, .188f))
    }

    @Test fun restoreKeepsAcceptedThrowsAndTheFinalResult() {
        var game = RingToss().throwAtPeg(0).pull(.20f, .10f)
        assertEquals(game, RingToss.restore(game.save()))
        repeat(3) { game = game.throwAtPeg(it) }
        val restored = RingToss.restore(game.save())
        assertEquals(game, restored)
        assertTrue(restored.complete)
        assertEquals(4, restored.hits)
    }

    @Test fun resultRequiresAllFiveThrowsButAllowsZeroHits() {
        var game = RingToss()
        repeat(RingToss.THROW_COUNT) {
            assertNull(DeedGameScore.fromRingToss(game))
            game = game.pull(.20f, .10f)
        }
        val score = checkNotNull(DeedGameScore.fromRingToss(game))
        assertEquals(DeedGameKind.RING_TOSS, score.kind)
        assertEquals(0, score.correct)
        assertEquals(RingToss.THROW_COUNT, score.attempts)
    }

    @Test fun malformedSavedBoardsCannotManufactureACompleteParty() {
        for (saved in listOf(null, floatArrayOf(), floatArrayOf(99f), floatArrayOf(1f, .5f),
            floatArrayOf(1f, Float.NaN, .29f), floatArrayOf(1f, .5f, Float.NEGATIVE_INFINITY),
            floatArrayOf(1f, 9f, .29f), floatArrayOf(1f) + FloatArray(12) { .5f })) {
            assertEquals(RingToss(), RingToss.restore(saved))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun directInvalidLandingIsRejected() { RingLanding(RingPoint(Float.NaN, .3f)) }

    @Test(expected = IllegalArgumentException::class)
    fun directTooManyThrowsAreRejected() {
        RingToss(List(6) { RingLanding(RingToss.pegs[0]) })
    }
}
