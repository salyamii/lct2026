package ru.nksk.lctapp.core.ui.components.tour

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class SpotlightPlacementTest {
    @Test fun goalCardFitsBetweenBothHighlightedTargets() {
        val header = Rect(16f, 12f, 320f, 56f)
        val button = Rect(240f, 640f, 340f, 738f)
        val region = spotlightCardRegion(800f, listOf(header, button), button, 12f, 210f)
        assertTrue(region.top > header.bottom)
        assertTrue(region.top + region.height < button.top)
    }

    @Test fun missingGeometryStillProvidesRoomForExitControls() {
        val region = spotlightCardRegion(580f, emptyList(), null, 12f, 210f)
        assertEquals(12f, region.top)
        assertEquals(556f, region.height)
    }

    @Test fun budgetPreviewLeavesTheCardBelowTheEntireExpandedPanel() {
        val arrow = Rect(140f, 60f, 188f, 108f)
        val preview = Rect(16f, 124f, 296f, 320f)
        val region = spotlightCardRegion(580f, listOf(arrow, preview), arrow, 12f, 210f)
        assertTrue(region.top > preview.bottom)
        assertTrue(region.top + region.height <= 568f)
    }
}
