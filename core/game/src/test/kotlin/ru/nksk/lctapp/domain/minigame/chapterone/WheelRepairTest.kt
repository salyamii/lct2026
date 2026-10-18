package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class WheelRepairTest {
    @Test fun `misaligned section cannot be secured`() {
        val game = WheelRepair()
        assertEquals(game, game.secure())
        assertEquals(game, game.rotateBy(Float.NaN))
    }

    @Test fun `wrap around alignment is forgiving and each section counts once`() {
        val first = WheelRepair().rotateBy(275f).secure()
        assertEquals(1, first.fixedCount)
        assertEquals(first, first.select(0))
        val second = first.rotateBy(-first.angle).secure()
        val complete = second.rotateBy(-second.angle).secure()
        assertTrue(complete.completed)
        assertEquals(3, complete.fixedCount)
        assertEquals(complete, complete.secure())
    }

    @Test fun `restoring validates active section and finite angle`() {
        val restored = WheelRepair.restore(1, 0, Float.POSITIVE_INFINITY)
        assertEquals(1, restored.selectedSection)
        assertTrue(restored.angle.isFinite())
        assertFalse(restored.completed)
    }

    @Test fun `button step can recover alignment after free rotation`() {
        val betweenSteps = WheelRepair().rotateBy(-75f)
        assertFalse(betweenSteps.aligned)
        val aligned = betweenSteps.rotateBy(-10f)
        assertTrue(aligned.aligned)
        assertEquals(1, aligned.secure().fixedCount)
    }
}
