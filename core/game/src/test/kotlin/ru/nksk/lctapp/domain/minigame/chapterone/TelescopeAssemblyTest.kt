package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.DeedGameScore

class TelescopeAssemblyTest {
    @Test fun `new games vary sky and focus independently and begin out of focus`() {
        val games = (0 until 1_000).map { TelescopeAssembly.create(Random(it)) }
        assertEquals((0 until TelescopeAssembly.SKY_VARIANT_COUNT).toSet(), games.map { it.skyVariant }.toSet())
        assertEquals(5, games.map { it.focusTarget }.toSet().size)
        assertEquals(25, games.map { it.skyVariant to it.focusTarget }.toSet().size)
        games.forEachIndexed { seed, game ->
            assertEquals(TelescopeStage.TRIPOD, game.stage)
            assertTrue(abs(game.focus - game.focusTarget) > TelescopeAssembly.FOCUS_TOLERANCE)
            assertFalse(game.completed)
            assertEquals(game, TelescopeAssembly.create(Random(seed)))
        }
    }

    @Test fun `restarting changes both sky and sharp focus even after a legacy game`() {
        var previous = TelescopeAssembly()
        repeat(100) { seed ->
            val next = TelescopeAssembly.create(Random(seed), previous)
            assertNotEquals(previous.skyVariant, next.skyVariant)
            assertTrue(abs(previous.focusTarget - next.focusTarget) > TelescopeAssembly.FOCUS_TOLERANCE * 2)
            previous = next
        }
    }

    @Test fun `assembly and focus actions keep the same sky and target`() {
        val initial = TelescopeAssembly.create(Random(41))
        val assembled = TelescopePart.entries.fold(initial) { game, part -> game.select(part).install(part) }
        val focused = assembled.adjustFocus(initial.focusTarget)
        assertEquals(initial.skyVariant, focused.skyVariant)
        assertEquals(initial.focusTarget, focused.focusTarget, 0f)
        assertTrue(focused.inFocus)
        assertEquals(1f, focused.sharpness, 0f)
        assertEquals(initial.skyVariant, focused.observe().skyVariant)
    }

    @Test fun `every new target is reachable and completion uses the current target`() {
        val games = (0 until 100).map { TelescopeAssembly.create(Random(it)) }.distinctBy { it.focusTarget }
        assertEquals(5, games.size)
        games.forEach { initial ->
            val assembled = TelescopePart.entries.fold(initial) { game, part -> game.install(part) }
            assertFalse(assembled.observe().completed)
            assertNull(DeedGameScore.fromTelescope(assembled))
            val finished = assembled.adjustFocus(initial.focusTarget).observe()
            assertTrue(finished.completed)
            assertNotNull(DeedGameScore.fromTelescope(finished))
            assertFalse(assembled.adjustFocus(initial.focusTarget + .09f).observe().completed)
            assertTrue(assembled.adjustFocus(initial.focusTarget - .08f).observe().completed)
            if (abs(initial.focusTarget - TelescopeAssembly.FOCUS_TARGET) > TelescopeAssembly.FOCUS_TOLERANCE) {
                assertFalse(assembled.adjustFocus(TelescopeAssembly.FOCUS_TARGET).observe().completed)
            }
        }
    }

    @Test fun `version four restores the same unfinished and completed sky`() {
        val initial = TelescopeAssembly.create(Random(82))
        val stages = buildList {
            add(initial)
            add(initial.install(TelescopePart.TRIPOD).select(TelescopePart.TUBE))
            add(TelescopePart.entries.fold(initial) { game, part -> game.install(part) })
            add(last().adjustFocus(initial.focusTarget).observe())
        }
        stages.forEach { state ->
            assertEquals(state, TelescopeAssembly.restore(
                state.installedCount, state.selectedPart?.ordinal, state.focus, state.completed,
                version = 4, skyVariant = state.skyVariant, focusTarget = state.focusTarget,
            ))
        }
    }

    @Test fun `legacy restores keep their original sky and focus including finished games`() {
        for (version in 1..3) {
            val restored = TelescopeAssembly.restore(
                3, null, .68f, true, version = version, skyVariant = 4, focusTarget = .24f,
            )
            assertEquals(0, restored.skyVariant)
            assertEquals(TelescopeAssembly.FOCUS_TARGET, restored.focusTarget, 0f)
            assertTrue(restored.completed)
        }
    }

    @Test fun `invalid saved sky or focus target cannot claim completion`() {
        listOf(-1, TelescopeAssembly.SKY_VARIANT_COUNT, Int.MAX_VALUE).forEach { variant ->
            val restored = TelescopeAssembly.restore(3, null, .68f, true, skyVariant = variant)
            assertEquals(0, restored.skyVariant)
            assertFalse(restored.completed)
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.1f, 1.1f).forEach { target ->
            val restored = TelescopeAssembly.restore(3, null, .68f, true, skyVariant = 2, focusTarget = target)
            assertEquals(TelescopeAssembly.FOCUS_TARGET, restored.focusTarget, 0f)
            assertTrue(restored.sharpness.isFinite())
            assertFalse(restored.completed)
        }
        val invalidFocusAtFallback = TelescopeAssembly.restore(
            3, null, Float.NaN, true, skyVariant = 2, focusTarget = .18f,
        )
        assertFalse(invalidFocusAtFallback.completed)
    }

    @Test fun `parts must be installed in order before focus is available`() {
        val initial = TelescopeAssembly()
        assertEquals(initial, initial.install(TelescopePart.LENS))
        assertEquals(initial, initial.install(TelescopePart.TUBE))
        assertFalse(initial.adjustFocus(.68f).observe().completed)
        val tripod = initial.install(TelescopePart.TRIPOD)
        assertEquals(TelescopeStage.TUBE, tripod.stage)
        assertEquals(tripod, tripod.install(TelescopePart.LENS))
        assertEquals(tripod, tripod.install(TelescopePart.TRIPOD))
    }

    @Test fun `seating tube secures mount and opens eyepiece placement immediately`() {
        val tube = mountedTelescope()
        assertEquals(TelescopeStage.EYEPIECE, tube.stage)
        assertEquals(TelescopePart.LENS, tube.nextPart)
        assertTrue(tube.mountSecured)
        assertEquals(TelescopeAssembly.MOUNT_TURN, tube.firstFastener, .001f)
        assertEquals(TelescopeAssembly.MOUNT_TURN, tube.secondFastener, .001f)
        assertFalse(tube.assembled)
    }

    @Test fun `placing eyepiece goes directly to focus without threading`() {
        val assembled = mountedTelescope().select(TelescopePart.LENS).install(TelescopePart.LENS)
        assertEquals(TelescopeStage.FOCUS, assembled.stage)
        assertTrue(assembled.assembled)
        assertTrue(assembled.eyepieceSecured)
        assertEquals(TelescopeAssembly.EYEPIECE_TURN, assembled.eyepieceTurn, .001f)
        assertNull(assembled.selectedPart)
        assertNull(assembled.nextPart)
        assertFalse(assembled.observe().completed)
    }

    @Test fun `assembly and sharp image are both required to finish`() {
        val assembled = mountedTelescope().install(TelescopePart.LENS)
        val finished = assembled.adjustFocus(.7f).observe()
        assertTrue(finished.completed)
        assertEquals(TelescopeStage.COMPLETE, finished.stage)
        assertEquals(finished, finished.adjustFocus(0f))
        assertEquals(assembled, assembled.adjustFocus(Float.NaN))
    }

    @Test fun `old mount save keeps tube and resumes at eyepiece placement`() {
        val restored = TelescopeAssembly.restore(2, 2, .18f, false, version = 2)
        assertEquals(TelescopeStage.EYEPIECE, restored.stage)
        assertEquals(TelescopePart.LENS, restored.selectedPart)
        assertTrue(restored.mountSecured)
        assertEquals(TelescopeAssembly.MOUNT_TURN, restored.firstFastener, .001f)
    }

    @Test fun `old threading save keeps eyepiece and resumes at focus without completing`() {
        val restored = TelescopeAssembly.restore(3, null, .68f, false, version = 2)
        assertEquals(TelescopeStage.FOCUS, restored.stage)
        assertTrue(restored.assembled)
        assertTrue(restored.inFocus)
        assertFalse(restored.completed)
    }

    @Test fun `completed old save remains complete only with assembled sharp telescope`() {
        for (version in 1..TelescopeAssembly.SAVE_VERSION) {
            assertTrue(TelescopeAssembly.restore(3, null, .68f, true, version).completed)
            assertFalse(TelescopeAssembly.restore(2, null, .68f, true, version).completed)
            assertFalse(TelescopeAssembly.restore(3, null, .18f, true, version).completed)
        }
    }

    @Test fun `invalid restored values cannot skip assembly or focus`() {
        val game = TelescopeAssembly.restore(-12, 100, Float.NaN, true)
        assertEquals(0, game.installedCount)
        assertNull(game.selectedPart)
        assertFalse(game.completed)
        assertTrue(game.focus.isFinite())
        val badFocus = TelescopeAssembly.restore(3, 0, Float.POSITIVE_INFINITY, true)
        assertFalse(badFocus.completed)
        assertNull(badFocus.selectedPart)
        assertEquals(TelescopeAssembly(), TelescopeAssembly.restore(3, null, .68f, true, version = 99))
    }

    private fun mountedTelescope() = TelescopeAssembly().install(TelescopePart.TRIPOD)
        .install(TelescopePart.TUBE)
}
