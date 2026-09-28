package ru.nksk.lctapp.core.ui.components

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test fun uniformColourStaysUniformThroughEdgesAndNarrowImages() {
        for ((width, height) in listOf(1 to 9, 9 to 1, 7 to 4)) {
            val pixels = IntArray(width * height) { 0xFF376B9D.toInt() }
            assertArrayEquals(pixels, blurBackdropPixels(pixels, width, height, radius = 2))
        }
    }

    @Test fun hardBoundarySoftensWithoutChangingCanvasOrInput() {
        val pixels = IntArray(25 * 3) { if (it % 25 < 12) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val original = pixels.copyOf()
        val blurred = blurBackdropPixels(pixels, 25, 3, radius = 1)
        assertArrayEquals(original, pixels)
        assertEquals(pixels.size, blurred.size)
        assertEquals(0xFF000000.toInt(), blurred[0])
        assertEquals(0xFFFFFFFF.toInt(), blurred[24])
        assertTrue((blurred[11] and 255) in 1..254)
        assertTrue((blurred[12] and 255) in 1..254)
        assertTrue((blurred[11] and 255) < (blurred[12] and 255))
        assertTrue(blurred.all { (it ushr 24) == 255 })
        assertArrayEquals(blurred.copyOfRange(0, 25), blurred.copyOfRange(50, 75))
    }
}
