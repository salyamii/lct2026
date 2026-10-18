package ru.nksk.lctapp.domain.minigame

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
        assertEquals(setOf(0), tapped.seen)
        assertEquals(0, tapped.recallMistakes)
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
    fun tap_closedCardDuringMismatch_startsNextMoveImmediately() {
        val state = MemoryState(faces = listOf(0, 0, 1, 1)).tap(0).tap(2)
        val next = state.tap(1)
        assertEquals(setOf(1), next.faceUp)
        assertNull(next.pending)
        assertEquals(1, next.moves)
        assertEquals(0, next.recallMistakes)
        assertEquals(setOf(0, 1, 2), next.seen)
        val matched = next.tap(0)
        assertEquals(setOf(0, 1), matched.matched)
        assertNull(matched.pending)
        assertEquals(2, matched.moves)
        assertEquals(0, matched.recallMistakes)
    }

    @Test
    fun tap_matchingPair_marksMatchedWithoutWaiting() {
        val state = MemoryState(faces = listOf(0, 0)).tap(0).tap(1)
        assertEquals(setOf(0, 1), state.matched)
        assertTrue(state.won)
        assertNull(state.pending)
        assertTrue(state.faceUp.isEmpty())
        assertEquals(1, state.moves)
        assertEquals(state, state.resolvePending())
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
            state = state.tap(first).tap(second)
        }
        assertTrue(state.won)
        assertEquals(faces.size, state.matched.size)
        assertEquals(4, state.moves)
        assertEquals(0, state.recallMistakes)
    }

    @Test
    fun deal_isRandomized() {
        val a = MemoryState.deal(random = Random(1))
        val b = MemoryState.deal(random = Random(2))
        assertFalse(a.faces == b.faces)
    }

    @Test
    fun exploratoryMismatchRecordsSeenPositionsWithoutChargingARecallMistake() {
        val pending = MemoryState(listOf(0, 0, 1, 1)).tap(0).tap(2)
        assertEquals(setOf(0, 2), pending.seen)
        assertEquals(1, pending.moves)
        assertEquals(0, pending.recallMistakes)
        val resolved = pending.resolvePending()
        assertEquals(pending.seen, resolved.seen)
        assertEquals(0, resolved.recallMistakes)
    }

    @Test
    fun mismatchChargesOnlyWhenTheFirstCardsMateWasAlreadyKnown() {
        val discovered = MemoryState(listOf(0, 0, 1, 1, 2, 2)).tap(1).tap(2).resolvePending()
        val first = discovered.tap(0)
        assertEquals(setOf(0, 1, 2), first.seen)
        assertEquals(0, first.recallMistakes)
        val pending = first.tap(4)
        assertEquals(1, pending.recallMistakes)
        assertEquals(2, pending.moves)
        assertEquals(1, pending.resolvePending().recallMistakes)
    }

    @Test
    fun newlyRevealedSecondCardDoesNotRetroactivelyPenalizeTheExploratoryFirstCard() {
        // We know B at index2. A's mate at index1 is still unknown when B at index3 is revealed.
        val discovered = MemoryState(listOf(0, 0, 1, 1, 2, 2)).tap(2).tap(4).resolvePending()
        val pending = discovered.tap(0).tap(3)
        assertEquals(0, pending.recallMistakes)
        val resolved = pending.resolvePending()
        assertEquals(0, resolved.recallMistakes)
        // This time B's mate was already known before the second tap.
        val missedKnownPair = resolved.tap(2).tap(5)
        assertEquals(1, missedKnownPair.recallMistakes)
    }

    @Test
    fun recallingTheCorrectPairPreservesPreviousMistakes() {
        val discovered = MemoryState(listOf(0, 0, 1, 1)).tap(1).tap(2).resolvePending()
        val mistake = discovered.tap(0).tap(2).resolvePending()
        val pendingCorrect = mistake.tap(0).tap(1)
        assertEquals(1, pendingCorrect.recallMistakes)
        val resolved = pendingCorrect.resolvePending()
        assertEquals(1, resolved.recallMistakes)
        assertEquals(setOf(0, 1), resolved.matched)
    }

    @Test
    fun ignoredTapsAndRepeatedResolutionDoNotAddSeenCardsOrMistakes() {
        val discovered = MemoryState(listOf(0, 0, 1, 1)).tap(1).tap(2).resolvePending()
        val first = discovered.tap(0)
        assertEquals(first, first.tap(0))
        assertEquals(first, first.tap(-1))
        assertEquals(first, first.tap(4))
        val pending = first.tap(2)
        assertEquals(pending, pending.tap(0))
        assertEquals(pending, pending.tap(2))
        assertEquals(pending, pending.tap(-1))
        assertEquals(pending, pending.tap(4))
        val resolved = pending.resolvePending()
        assertEquals(resolved, resolved.resolvePending())
        val matched = resolved.tap(0).tap(1).resolvePending()
        assertEquals(matched, matched.tap(0))
        val won = matched.tap(2).tap(3).resolvePending()
        assertTrue(won.won)
        assertEquals(won, won.tap(0))
        assertEquals(1, won.recallMistakes)
        assertEquals(setOf(0, 1, 2, 3), won.seen)
    }
}
