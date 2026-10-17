package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackingStateTest {
    private val rounds = listOf(
        PackingRound("Поход", listOf("фонарь", "плащ", "термос", "змей"), oddIndex = 3),
        PackingRound("Рыбалка", listOf("удочка", "ведро", "плащ", "шкатулка"), oddIndex = 2),
    )

    @Test
    fun create_shufflesItemsKeepingOddAnswer() {
        val state = PackingState.create(random = Random(4))
        assertEquals(PackingState.ROUNDS, state.rounds.size)
        state.rounds.forEach { round ->
            assertEquals(PackingState.ITEM_COUNT, round.items.size)
            assertEquals(round.items.distinct(), round.items)
            assertTrue(round.items[round.oddIndex].isNotBlank())
        }
    }

    @Test
    fun pick_oddItem_countsCorrect() {
        val state = PackingState(rounds.take(1))
        val picked = state.pick(3)
        assertEquals(true, picked.lastCorrect)
        assertEquals(1, picked.correctRounds)
    }

    @Test
    fun pick_regularItem_countsIncorrect() {
        val picked = PackingState(rounds.take(1)).pick(0)
        assertEquals(false, picked.lastCorrect)
    }

    @Test
    fun pick_twiceWithoutNext_isIgnored() {
        val picked = PackingState(rounds.take(1)).pick(3)
        assertEquals(picked, picked.pick(0))
    }

    @Test
    fun afterLastRound_gameFinishes() {
        val threeRounds = rounds + PackingRound("Ремонт", listOf("молоток", "гвозди", "доска", "свечи"), 3)
        var state = PackingState(threeRounds)
        repeat(PackingState.ROUNDS) { state = state.pick(3).next() }
        assertTrue(state.finished)
        assertNotNull(DeedGameScore.fromPacking(state))
    }

    @Test
    fun unfinishedGame_producesNoScore() {
        assertNull(DeedGameScore.fromPacking(PackingState(rounds).pick(3)))
        assertFalse(PackingState(rounds).finished)
    }
}
