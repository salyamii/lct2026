package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetStopStateTest {
    @Test
    fun stoppedRoundKeepsTheZoneUsedToJudgeItsHit() {
        val state = TargetStopState(zoneStart = 40)
        val stopped = state.stop(50)
        assertEquals(40, stopped.zoneStart)
        assertEquals(true, stopped.lastHit)
    }

    @Test
    fun create_preparesFirstRound() {
        val state = TargetStopState.create(random = Random(7))
        assertEquals(0, state.round)
        assertTrue(state.zoneStart in TargetStopState.MIN_ZONE_START..TargetStopState.MAX_ZONE_START)
    }

    @Test
    fun stop_insideZone_countsHit() {
        val state = TargetStopState(zoneStart = 40)
        val stopped = state.stop(50)
        assertEquals(true, stopped.lastHit)
        assertEquals(1, stopped.hits)
        assertEquals(1, stopped.round)
        assertEquals(2, stopped.reward)
    }

    @Test
    fun stop_outsideZone_countsMiss() {
        val state = TargetStopState(zoneStart = 40)
        val stopped = state.stop(10)
        assertEquals(false, stopped.lastHit)
        assertEquals(0, stopped.hits)
    }

    @Test
    fun stop_zoneUpperEdge_isInclusive() {
        val state = TargetStopState(zoneStart = 40)
        val stopped = state.stop(59)
        assertEquals(true, stopped.lastHit)
    }

    @Test
    fun stop_beyondHundred_isClamped() {
        val state = TargetStopState(zoneStart = 90)
        val stopped = state.stop(500)
        assertEquals(true, stopped.lastHit)
    }

    @Test
    fun stop_twiceWithoutNext_isIgnored() {
        val state = TargetStopState(zoneStart = 40)
        val stopped = state.stop(50)
        assertEquals(stopped, stopped.stop(50))
    }

    @Test
    fun next_clearsFeedbackAndKeepsScore() {
        val state = TargetStopState(zoneStart = 40)
        val next = state.stop(50).next()
        assertNull(next.lastHit)
        assertEquals(1, next.round)
        assertEquals(1, next.hits)
    }

    @Test
    fun afterFiveStops_gameFinishes() {
        var state = TargetStopState.create(random = Random(1))
        repeat(TargetStopState.ROUNDS) {
            state = state.stop(state.zoneStart).next()
        }
        assertTrue(state.finished)
        assertEquals(TargetStopState.ROUNDS, state.round)
        assertEquals(TargetStopState.ROUNDS * TargetStopState.REWARD_PER_HIT, state.reward)
    }

    @Test
    fun stop_afterFinish_isIgnored() {
        val state = TargetStopState(zoneStart = 40, round = TargetStopState.ROUNDS)
        assertEquals(state, state.stop(50))
    }
}
