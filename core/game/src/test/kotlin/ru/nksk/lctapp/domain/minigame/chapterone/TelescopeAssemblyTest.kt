package ru.nksk.lctapp.domain.minigame.chapterone

import org.junit.Assert.*
import org.junit.Test

class TelescopeAssemblyTest {
    @Test fun `lens cannot be installed before support and tube`() {
        val initial = TelescopeAssembly()
        assertEquals(initial, initial.install(TelescopePart.LENS))
        assertFalse(initial.adjustFocus(.68f).observe().completed)
    }

    @Test fun `assembly and sharp image are both required to finish`() {
        val assembled = mountedTelescope().install(TelescopePart.LENS)
            .turnEyepiece(TelescopeAssembly.EYEPIECE_TURN).secureEyepiece()
        assertTrue(assembled.assembled)
        assertFalse(assembled.observe().completed)
        val finished = assembled.adjustFocus(.7f).observe()
        assertTrue(finished.completed)
        assertEquals(finished, finished.adjustFocus(0f))
    }

    @Test fun `invalid restored state cannot skip assembly or focus`() {
        val game = TelescopeAssembly.restore(-12, 100, Float.NaN, true)
        assertEquals(0, game.installedCount)
        assertNull(game.selectedPart)
        assertFalse(game.completed)
        assertTrue(game.focus.isFinite())
    }

    @Test fun `both mount fasteners must be tightened before eyepiece can be inserted`() {
        val tube = TelescopeAssembly().install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
        assertEquals(TelescopeStage.MOUNT, tube.stage)
        assertEquals(tube, tube.install(TelescopePart.LENS))
        val oneFastener = tube.turnFastener(0, TelescopeAssembly.MOUNT_TURN)
        assertFalse(oneFastener.mountReady)
        assertEquals(oneFastener, oneFastener.secureMount())
        val tightened = oneFastener.turnFastener(1, TelescopeAssembly.MOUNT_TURN)
        assertTrue(tightened.mountReady)
        assertEquals(TelescopeStage.EYEPIECE, tightened.secureMount().stage)
    }

    @Test fun `turning backwards loosens selected fastener without losing the other`() {
        val tube = TelescopeAssembly().install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
            .turnFastener(0, 180f).turnFastener(1, 180f)
        val loosened = tube.turnFastener(0, -45f)
        assertEquals(135f, loosened.firstFastener, .001f)
        assertEquals(180f, loosened.secondFastener, .001f)
        assertFalse(loosened.mountReady)
        assertEquals(0f, loosened.turnFastener(0, -500f).firstFastener, .001f)
        assertEquals(loosened, loosened.turnFastener(0, Float.NaN))
    }

    @Test fun `placing eyepiece is not enough to reach focus and reverse turn unwinds it`() {
        val inserted = mountedTelescope().install(TelescopePart.LENS)
        assertEquals(TelescopeStage.THREAD, inserted.stage)
        assertEquals(inserted, inserted.adjustFocus(.68f).secureEyepiece())
        val tightened = inserted.turnEyepiece(TelescopeAssembly.EYEPIECE_TURN)
        assertTrue(tightened.eyepieceReady)
        val loosened = tightened.turnEyepiece(-45f)
        assertFalse(loosened.eyepieceReady)
        assertEquals(TelescopeStage.THREAD, loosened.secureEyepiece().stage)
        assertEquals(TelescopeStage.FOCUS, tightened.secureEyepiece().stage)
    }

    @Test fun `old completed save cannot skip the new assembly steps`() {
        assertEquals(TelescopeAssembly(), TelescopeAssembly.restore(3, null, .68f, true, version = 1))
    }

    @Test fun `small angle difference is accepted without requiring exact finger position`() {
        val tube = TelescopeAssembly().install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
            .turnFastener(0, 174f).turnFastener(1, 174f)
        assertTrue(tube.mountReady)
        val secured = tube.secureMount()
        assertEquals(180f, secured.firstFastener, .001f)
        val eyepiece = secured.install(TelescopePart.LENS).turnEyepiece(264f)
        assertTrue(eyepiece.eyepieceReady)
        assertEquals(TelescopeStage.FOCUS, eyepiece.secureEyepiece().stage)
    }

    @Test fun `current save restores partial turns but rejects unearned confirmations`() {
        val partial = TelescopeAssembly.restore(2, null, .18f, false,
            firstFastener = 135f, secondFastener = 45f, selectedFastener = 1)
        assertEquals(TelescopeStage.MOUNT, partial.stage)
        assertEquals(135f, partial.firstFastener, .001f)
        assertEquals(45f, partial.secondFastener, .001f)
        assertEquals(1, partial.selectedFastener)
        val invalid = TelescopeAssembly.restore(3, null, .68f, true,
            firstFastener = 0f, secondFastener = 180f, mountSecured = true,
            eyepieceTurn = 270f, eyepieceSecured = true)
        assertEquals(TelescopeStage.MOUNT, invalid.stage)
        assertFalse(invalid.assembled)
        assertFalse(invalid.completed)
    }

    @Test fun `release advances only after both screws have been tightened`() {
        val tube = TelescopeAssembly().install(TelescopePart.TRIPOD).install(TelescopePart.TUBE)
        assertEquals(tube, tube.finishTurning())
        val one = tube.turnFastener(0, 180f)
        assertEquals(one, one.finishTurning())
        val two = one.turnFastener(1, 180f)
        assertEquals(TelescopeStage.MOUNT, two.stage)
        assertEquals(TelescopeStage.EYEPIECE, two.finishTurning().stage)
    }

    @Test fun `release does not advance after a reverse turn loosens the eyepiece`() {
        val threaded = mountedTelescope().install(TelescopePart.LENS).turnEyepiece(270f)
        assertEquals(TelescopeStage.THREAD, threaded.stage)
        val loosened = threaded.turnEyepiece(-45f)
        assertEquals(loosened, loosened.finishTurning())
        assertEquals(TelescopeStage.FOCUS, threaded.finishTurning().stage)
        assertEquals(TelescopeAssembly(), TelescopeAssembly().finishTurning())
    }

    private fun mountedTelescope() = TelescopeAssembly().install(TelescopePart.TRIPOD)
        .install(TelescopePart.TUBE).turnFastener(0, TelescopeAssembly.MOUNT_TURN)
        .turnFastener(1, TelescopeAssembly.MOUNT_TURN).secureMount()
}
