package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedWantStateTest {
    private val items = listOf(
        NeedWantItem("хлеб", need = true), NeedWantItem("плащ", need = true),
        NeedWantItem("вода", need = true), NeedWantItem("мазь", need = true),
        NeedWantItem("змей", need = false), NeedWantItem("сладости", need = false),
        NeedWantItem("шкатулка", need = false), NeedWantItem("украшение", need = false),
    )

    @Test
    fun create_hasHalfNeedsAndHalfWants() {
        val state = NeedWantState.create(random = Random(3))
        assertEquals(NeedWantState.ITEM_COUNT, state.items.size)
        assertEquals(NeedWantState.ITEM_COUNT / 2, state.items.count { it.need })
    }

    @Test
    fun answer_matchingTag_countsCorrect() {
        val state = NeedWantState(items)
        val answered = state.answer(saidNeed = true)
        assertEquals(true, answered.lastCorrect)
        assertEquals(1, answered.correctAnswers)
    }

    @Test
    fun answer_twiceWithoutNext_isIgnored() {
        val state = NeedWantState(items)
        val answered = state.answer(saidNeed = true)
        assertEquals(answered, answered.answer(saidNeed = false))
    }

    @Test
    fun afterLastItem_gameFinishes() {
        var state = NeedWantState(items)
        repeat(NeedWantState.ITEM_COUNT) { state = state.answer(saidNeed = true).next() }
        assertTrue(state.finished)
        assertNotNull(DeedGameScore.fromNeedWant(state))
    }

    @Test
    fun unfinishedGame_producesNoScore() {
        assertNull(DeedGameScore.fromNeedWant(NeedWantState(items).answer(saidNeed = true)))
        assertFalse(NeedWantState(items).finished)
    }
}
