package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrapRepairTest {
    @Test fun alignmentUsesWideToleranceButRejectsTheWrongPlace() {
        val start = StrapRepair()
        assertEquals(start, start.alignAt(.5f, -.26f))
        assertEquals(start, start.alignAt(0f, .26f))
        assertEquals(start, start.alignAt(Float.NaN, -.26f))
        assertEquals(StrapPhase.STITCH, start.alignAt(.08f, -.19f).phase)
    }

    @Test fun stitchesNeedAlignedMaterialAndTheExpectedPoint() {
        val start = StrapRepair()
        assertEquals(start, start.stitch(0))
        val aligned = start.align()
        assertEquals(aligned, aligned.stitch(2))
        val one = aligned.stitch(0)
        assertEquals(one, one.stitch(0))
        assertEquals(1, one.stitchedPoints)
    }

    @Test fun oneContinuousFastStrokeCanCrossTheNextHole() {
        val aligned = StrapRepair().align()
        val crossed = aligned.trace(StrapPoint(.15f, .39f), StrapPoint(.48f, .39f))
        assertEquals(1, crossed.stitchedPoints)
        assertEquals(aligned, aligned.trace(StrapPoint(.1f, .9f), StrapPoint(.9f, .9f)))
    }

    @Test fun everyTargetCanAlsoBeTapped() {
        var repair = StrapRepair().align()
        StrapRepair.points.forEach { point -> repair = repair.trace(point, point) }
        assertEquals(StrapPhase.TIGHTEN, repair.phase)
        assertEquals(StrapRepair.STITCH_COUNT, repair.stitchedPoints)
        assertFalse(repair.knotTightened)
    }

    @Test fun crossingTheCentreBeforeTheFourthHoleDoesNotCountTheFifthHole() {
        val repair = StrapRepair().align().stitch(0).stitch(1).stitch(2)
        val crossed = repair.trace(StrapRepair.points[2], StrapRepair.points[3])
        assertEquals(4, crossed.stitchedPoints)
        assertEquals(StrapPhase.STITCH, crossed.phase)
    }

    @Test fun onlyTheFinalPullCompletesTheRepair() {
        var repair = StrapRepair().align()
        assertEquals(repair, repair.tighten())
        repeat(StrapRepair.STITCH_COUNT) { repair = repair.stitch(it) }
        assertEquals(repair, repair.pull(.05f))
        assertEquals(repair, repair.pull(Float.POSITIVE_INFINITY))
        assertEquals(StrapPhase.DONE, repair.pull(.16f).phase)
        assertTrue(repair.tighten().knotTightened)
    }

    @Test fun restoringAnIncompleteOrInvalidRoundNeverInventsCompletion() {
        assertEquals(StrapPhase.STITCH, StrapRepair.restore(true, 3, false).phase)
        assertEquals(StrapPhase.TIGHTEN, StrapRepair.restore(true, 5, false).phase)
        assertEquals(StrapPhase.ALIGN, StrapRepair.restore(true, 3, true).phase)
        assertEquals(StrapPhase.ALIGN, StrapRepair.restore(false, 5, true).phase)
        assertEquals(StrapPhase.ALIGN, StrapRepair.restore(true, 9, true).phase)
    }

    @Test fun completedRepairIgnoresLateInput() {
        var repair = StrapRepair().align()
        repeat(StrapRepair.STITCH_COUNT) { repair = repair.stitch(it) }
        repair = repair.tighten()
        assertEquals(repair, repair.align().stitch(0).pull(.2f))
    }
}
