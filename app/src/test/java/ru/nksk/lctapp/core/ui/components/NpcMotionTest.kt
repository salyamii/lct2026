package ru.nksk.lctapp.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcMotionTest {
    @Test fun portraitCanvasKeepsItsContactWhenFittedInsideAWideScene() {
        val contact = NpcArtworkContact(100f, 200f, 60f, 180f)
        val pivot = contact.fittedPivot(width = 300f, height = 200f)
        assertEquals(160f / 300f, pivot.pivotFractionX, .00001f)
        assertEquals(.9f, pivot.pivotFractionY, .00001f)
    }

    @Test fun transparentFootMarginIsPreservedInsideATallScene() {
        val contact = NpcArtworkContact(100f, 100f, 55f, 85f)
        val pivot = contact.fittedPivot(width = 200f, height = 400f)
        assertEquals(.55f, pivot.pivotFractionX, .00001f)
        assertEquals(270f / 400f, pivot.pivotFractionY, .00001f)
    }

    @Test fun idleLoopClosesWithoutJumpingAndRemainsSubtle() {
        val start = npcIdlePose(0f)
        val end = npcIdlePose(1f)
        assertEquals(start.rotation, end.rotation, .00001f)
        assertEquals(start.scaleX, end.scaleX, .00001f)
        assertEquals(start.scaleY, end.scaleY, .00001f)
        for (step in 0..100) {
            val pose = npcIdlePose(step / 100f)
            assertTrue(pose.scaleX in .99f..1f)
            assertTrue(pose.scaleY in 1f..1.01f)
            assertTrue(pose.rotation in -0.5f..0.5f)
        }
    }
}
