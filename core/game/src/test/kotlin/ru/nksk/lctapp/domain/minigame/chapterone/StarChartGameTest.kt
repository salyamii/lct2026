package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class StarChartGameTest {
    @Test fun chartAcceptsEitherDirectionButNotUnrelatedStars() {
        var state = StarChartGame.select(StarChartState(), 1)
        state = StarChartGame.select(state, 0)
        assertEquals(setOf(0), state.joined)
        state = StarChartGame.select(state, 3)
        assertEquals(setOf(0), state.joined)
        assertFalse(state.complete)
    }

    @Test fun repeatedEdgeDoesNotCompleteChart() {
        var state = StarChartState()
        repeat(20) { state = StarChartGame.select(StarChartGame.select(state, 0), 1) }
        assertEquals(1, state.joined.size)
        StarChartGame.edges.forEach { (a, b) ->
            state = StarChartGame.select(StarChartGame.select(state.copy(selected = null), a), b)
        }
        assertTrue(state.complete)
        assertNull(state.selected)
    }
}
