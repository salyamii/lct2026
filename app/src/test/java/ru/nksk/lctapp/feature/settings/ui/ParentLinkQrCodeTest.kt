package ru.nksk.lctapp.feature.settings.ui

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ParentLinkQrCodeTest {
    @Test fun integerModulesRetainAtLeastFourWhiteModulesOnEveryEdge() {
        for (modules in listOf(21, 29, 57, 177)) for (side in listOf(185, 288, 689)) {
            val geometry = checkNotNull(qrGeometry(side, modules))
            val firstDarkPixel = geometry.leadingPixels + 4 * geometry.modulePixels
            val lastDarkPixelExclusive = firstDarkPixel + modules * geometry.modulePixels
            assertTrue(geometry.modulePixels > 0)
            assertTrue(firstDarkPixel >= 4 * geometry.modulePixels)
            assertTrue(side - lastDarkPixelExclusive >= 4 * geometry.modulePixels)
            assertEquals(modules + 8, geometry.totalModules)
        }
    }

    @Test fun tooSmallCanvasNeverDrawsSubpixelModules() {
        assertNull(qrGeometry(28, 21))
        assertEquals(1, checkNotNull(qrGeometry(29, 21)).modulePixels)
    }

    @Test fun profileUuidProducesARealFinderPatternAndDeterministicCode() = runTest {
        val matrix = encodeParentLinkQr("ad64c0e4-2731-4c1e-9fc9-bac812fd5995")
        assertEquals(matrix, encodeParentLinkQr("ad64c0e4-2731-4c1e-9fc9-bac812fd5995"))
        // Top-left QR finder has an exact black/white/black 7-module pattern.
        for (y in 0..6) for (x in 0..6) {
            val finderDark = x == 0 || y == 0 || x == 6 || y == 6 || (x in 2..4 && y in 2..4)
            assertEquals("finder module $x/$y", finderDark, matrix.dark(x, y))
        }
        assertTrue((0 until matrix.size).any { !matrix.dark(it, 7) })
    }

}
