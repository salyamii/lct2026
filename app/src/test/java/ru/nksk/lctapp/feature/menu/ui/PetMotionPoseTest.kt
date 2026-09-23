package ru.nksk.lctapp.feature.menu.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetMotionPoseTest {
    @Test fun idleLoopClosesWithoutAJumpAndKeepsMotionSubtle() {
        val start = petIdlePose(0f)
        val end = petIdlePose(1f)
        assertEquals(start.x, end.x, .00001f)
        assertEquals(start.rotation, end.rotation, .00001f)
        assertEquals(start.scaleX, end.scaleX, .00001f)
        assertEquals(start.scaleY, end.scaleY, .00001f)
        for (step in 0..100) {
            val pose = petIdlePose(step / 100f)
            assertTrue(pose.x in -0.0031f..0.0031f)
            assertTrue(pose.rotation in -0.651f..0.651f)
            assertTrue(pose.scaleX in .9959f..1f)
            assertTrue(pose.scaleY in 1f..1.0121f)
        }
    }

    @Test fun zeroIntensityKeepsTheOriginalPose() {
        for (step in 0..100) {
            val pose = petIdlePose(step / 100f, intensity = 0f)
            assertEquals(0f, pose.x, 0f)
            assertEquals(0f, pose.rotation, 0f)
            assertEquals(1f, pose.scaleX, 0f)
            assertEquals(1f, pose.scaleY, 0f)
        }
    }
}
