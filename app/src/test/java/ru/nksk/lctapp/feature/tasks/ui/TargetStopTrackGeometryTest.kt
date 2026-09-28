package ru.nksk.lctapp.feature.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.DeedGameScore
import ru.nksk.lctapp.domain.minigame.TargetStopState

class TargetStopTrackGeometryTest {
    @Test fun theAuditedPositionsUseTheSameVisibleZoneAndScoringScale() {
        val geometry = TargetStopTrackGeometry(width = 320f, markerSize = 26f)
        val left = geometry.centerAt(.05f)
        val right = geometry.centerAt(.25f)
        assertEquals(27.7f, left, .001f)
        assertEquals(86.5f, right, .001f)
        assertTrue(geometry.centerAt(.04f) < left)
        assertTrue(geometry.centerAt(.24f) in left..<right)

        val finalRound = TargetStopState(zoneStart = 5, round = 4, hits = 3)
        val miss = finalRound.stop(4)
        val hit = finalRound.stop(24)
        assertFalse(checkNotNull(miss.lastHit))
        assertTrue(checkNotNull(hit.lastHit))
        assertEquals(6L, checkNotNull(DeedGameScore.fromPrecision(miss)).reward(10))
        assertEquals(8L, checkNotNull(DeedGameScore.fromPrecision(hit)).reward(10))
    }

    @Test fun bothZoneEdgesAgreeWithScoringAcrossWidthsAndMarkerSizes() {
        for (width in listOf(160f, 320f, 640f)) {
            for (markerSize in listOf(12f, 26f, 40f)) {
                val geometry = TargetStopTrackGeometry(width, markerSize)
                for (zone in listOf(5, 40, 75)) {
                    val left = geometry.centerAt(zone / 100f)
                    val right = geometry.centerAt((zone + 20) / 100f)
                    for ((position, expectedHit) in listOf(zone - 1 to false, zone to true,
                        zone + 19 to true, zone + 20 to false)) {
                        val center = geometry.centerAt(position / 100f)
                        assertEquals("$width/$markerSize/$zone/$position", expectedHit, center >= left && center < right)
                        assertEquals(expectedHit, TargetStopState(zone).stop(position).lastHit)
                    }
                }
            }
        }
    }
}
