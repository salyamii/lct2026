package ru.nksk.lctapp.core.ui.components.tour

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class SpotlightArrowTest {
    private val screen = Rect(0f, 0f, 390f, 844f)

    @Test fun connectorDetoursAroundExpandedBudgetInsteadOfCrossingItsText() {
        val popup = Rect(16f, 170f, 280f, 520f)
        val target = Rect(100f, 110f, 148f, 158f)
        val card = Rect(16f, 540f, 374f, 790f)
        val route = spotlightArrowRoute(card, target, listOf(popup), screen, 8f, 24f)
        assertTrue(route.size >= 3)
        assertTrue(route.first().x > popup.right)
        assertEquals(Offset(target.right, target.center.y), route.last())
        assertTrue(route.zipWithNext().all { (a, b) -> !segmentCrossesRect(a, b, popup.inflate(7f)) })
    }

    @Test fun unobstructedConnectorRemainsStraight() {
        val route = spotlightArrowRoute(Rect(16f, 200f, 374f, 480f), Rect(100f, 40f, 160f, 100f),
            emptyList(), screen, 8f, 24f)
        assertEquals(listOf(Offset(130f, 200f), Offset(130f, 100f)), route)
    }

    @Test fun noSafeCorridorOmitsConnectorInsteadOfDrawingAcrossHighlight() {
        val route = spotlightArrowRoute(Rect(16f, 540f, 374f, 790f), Rect(100f, 40f, 160f, 100f),
            listOf(Rect(0f, 120f, 390f, 530f)), screen, 8f, 24f)
        assertTrue(route.isEmpty())
    }
}
