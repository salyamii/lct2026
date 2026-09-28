package ru.nksk.lctapp.feature.map.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCanvasSizeTest {
    @Test fun portraitMapFitsBothDimensionsWithoutDistortion() {
        for ((width, height) in listOf(360f to 600f, 390f to 700f, 600f to 1000f, 360f to 900f)) {
            val size = mapCanvasSize(width, height, fontScale = 1f)
            assertFalse(size.scrollable)
            assertTrue(size.width <= width)
            assertTrue(size.height <= height + .001f)
            assertEquals(1844f / 853f, size.height / size.width, .0001f)
        }
    }

    @Test fun shortWindowsAndLargeFontsKeepReadableMapScaleWithScrolling() {
        for ((height, scale) in listOf(260f to 1f, 480f to 1f, 600f to 1.5f)) {
            val size = mapCanvasSize(390f, height, fontScale = scale)
            assertTrue(size.scrollable)
            assertEquals(390f, size.width, 0f)
            assertTrue(size.height > height)
            assertEquals(1844f / 853f, size.height / size.width, .0001f)
        }
    }

    @Test fun mapAlreadyFittingAtFullWidthDoesNotNeedScrolling() {
        val size = mapCanvasSize(200f, 500f, fontScale = 1f)
        assertFalse(size.scrollable)
        assertEquals(200f, size.width, 0f)
        assertTrue(size.height < 500f)
    }
}
