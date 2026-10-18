package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class RouteMapGameTest {
    @Test fun roadCanBeDrawnInEitherDirectionAndResumed() {
        val partial = (7 downTo 4).fold(StarChartState(), RouteMapGame::select)
        assertEquals(setOf(4, 5, 6), partial.joined)
        val restored = RouteMapGame.restore(partial.joined.toList(), partial.selected)
        val complete = (3 downTo 0).fold(restored, RouteMapGame::select)
        assertTrue(complete.complete)
        assertNull(complete.selected)
    }

    @Test fun towerEndpointAndInvalidSavedDataAreHandledByRouteGeometry() {
        assertEquals(7, RouteMapGame.restore(emptyList(), 7).selected)
        assertNull(StarChartGame.restore(emptyList(), 7).selected)
        assertFalse(RouteMapGame.restore(listOf(-1, 0, 1, 2, 3, 4, 5, 10), 20).complete)
        assertFalse(StarChartState((1..7).toSet()).complete)
        val jump = RouteMapGame.select(RouteMapGame.select(StarChartState(), 0), 7)
        assertTrue(jump.joined.isEmpty())
    }
}
