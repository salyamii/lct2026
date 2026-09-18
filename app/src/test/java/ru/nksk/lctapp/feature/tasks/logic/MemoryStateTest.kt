package ru.nksk.lctapp.feature.tasks.logic

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStateTest {
    @Test
    fun deal_placesEachFaceExactlyTwice() {
        val state = MemoryState.deal(pairs = 8, random = Random(42))
        assertEquals(MemoryState.PAIRS * 2, state.faces.size)
        assertEquals(MemoryState.PAIRS, state.faces.toSet().size)
        state.faces.groupingBy { it }.eachCount().values.forEach { assertEquals(2, it) }
    }

    @Test
    fun tap_opensSingleCard() {
        val state = MemoryState(faces = listOf(0, 0))
        val tapped = state.tap(0)
        assertEquals(setOf(0), tapped.faceUp)
        assertNull(tapped.pending)
        assertEquals(0, tapped.moves)
    }

    @Test
    fun tap_sameCardTwice_isIgnored() {
        val state = MemoryState(faces = listOf(0, 0)).tap(0)
        assertEquals(state, state.tap(0))
    }

    @Test
    fun tap_secondCard_createsPendingAndCountsMove() {
        val state = MemoryState(faces = listOf(0, 1)).tap(0).tap(1)
        assertEquals(PendingPair(0, 1), state.pending)
        assertEquals(setOf(0, 1), state.faceUp)
        assertEquals(1, state.moves)
    }

    @Test
    fun tap_whilePending_isIgnored() {
        val state = MemoryState(faces = listOf(0, 1, 1)).tap(0).tap(1)
        assertEquals(state, state.tap(2))
    }

    @Test
    fun resolvePending_matchingPair_marksMatched() {
        val state = MemoryState(faces = listOf(0, 0)).tap(0).tap(1)
        val resolved = state.resolvePending()
        assertEquals(setOf(0, 1), resolved.matched)
        assertTrue(resolved.won)
        assertNull(resolved.pending)
        assertTrue(resolved.faceUp.isEmpty())
    }

    @Test
    fun resolvePending_mismatch_flipsBack() {
        val state = MemoryState(faces = listOf(0, 1)).tap(0).tap(1)
        val resolved = state.resolvePending()
        assertTrue(resolved.matched.isEmpty())
        assertTrue(resolved.faceUp.isEmpty())
        assertFalse(resolved.won)
    }

    @Test
    fun fullGame_reachesWon() {
        val faces = listOf(0, 1, 2, 3, 0, 1, 2, 3)
        var state = MemoryState(faces = faces)
        for (first in faces.indices) {
            if (first in state.matched) continue
            val second = faces.withIndex().first { it.index != first && it.value == faces[first] && it.index !in state.matched }.index
            state = state.tap(first).tap(second).resolvePending()
        }
        assertTrue(state.won)
        assertEquals(faces.size, state.matched.size)
    }

    @Test
    fun deal_isRandomized() {
        val a = MemoryState.deal(random = Random(1))
        val b = MemoryState.deal(random = Random(2))
        assertFalse(a.faces == b.faces)
    }
}
