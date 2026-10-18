package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class MarkGameTest {
    @Test fun tracingCannotSkipCleaningAndUnrelatedTouchesDoNotAdvance() {
        var state = MarkState()
        assertFalse(state.readyToTrace)
        MarkGame.dust.forEach { state = MarkGame.touch(state, it.x, it.y) }
        assertTrue(state.readyToTrace)
        val before = state
        assertEquals(before, MarkGame.touch(state, 0f, 0f))
        MarkGame.strokes.forEach { stroke -> stroke.forEach { state = MarkGame.touch(state, it.x, it.y) } }
        assertTrue(state.complete)
    }

    @Test fun restoredTraceRequiresEntireCleanSurface() {
        assertEquals(MarkState(setOf(0)), MarkGame.restore(listOf(0, 80), 4, 0))
        assertFalse(MarkGame.restore(emptyList(), 99, 99).complete)
    }
}
