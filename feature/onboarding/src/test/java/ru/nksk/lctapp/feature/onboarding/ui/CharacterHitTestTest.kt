package ru.nksk.lctapp.feature.onboarding.ui

import org.junit.Assert.*
import org.junit.Test

class CharacterHitTestTest {
    @Test fun transparentForegroundFallsThroughToVisibleCharacter() {
        val back = HitLayer(0f, 0f, 20f, 20f, AlphaMask(2, 2, byteArrayOf(-1, -1, -1, -1)))
        val front = HitLayer(0f, 0f, 20f, 20f, AlphaMask(2, 2, byteArrayOf(0, -1, 0, 0)))
        assertEquals(0, hitCharacter(listOf(back, front), 2f, 2f))
        assertEquals(1, hitCharacter(listOf(back, front), 12f, 2f))
        assertNull(hitCharacter(listOf(back, front), 20f, 2f))
        assertNull(hitCharacter(listOf(back, front), -1f, 2f))
    }

    @Test fun hitTestingUsesDisplayedScaleAndOffset() {
        val layer = HitLayer(10f, 20f, 40f, 80f, AlphaMask(2, 2, byteArrayOf(0, 0, -1, 0)))
        assertEquals(0, hitCharacter(listOf(layer), 15f, 70f))
        assertNull(hitCharacter(listOf(layer), 35f, 70f))
        assertNull(hitCharacter(listOf(layer), 15f, 30f))
    }
}
