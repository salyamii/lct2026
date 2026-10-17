package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountStateTest {
    @Test
    fun create_mixesRealAndFakeDiscounts() {
        val state = DiscountState.create(random = Random(5))
        assertEquals(DiscountState.ROUNDS, state.questions.size)
        assertTrue(state.questions.any { it.realDiscount })
        assertTrue(state.questions.any { !it.realDiscount })
        state.questions.forEach { assertTrue(it.stickerOld > it.priceNow) }
    }

    @Test
    fun answer_matchingBelief_countsCorrect() {
        val state = DiscountState(listOf(DiscountQuestion("фонарь", stickerOld = 60, priceNow = 40, fairPrice = 50)))
        val answered = state.answer(believedReal = true)
        assertEquals(true, answered.lastCorrect)
        assertEquals(1, answered.correctAnswers)
    }

    @Test
    fun answer_fakeDiscountFlaggedIncorrect() {
        val state = DiscountState(listOf(DiscountQuestion("фонарь", stickerOld = 60, priceNow = 55, fairPrice = 50)))
        val answered = state.answer(believedReal = true)
        assertEquals(false, answered.lastCorrect)
    }

    @Test
    fun afterLastAnswer_gameFinishes() {
        var state: DiscountState = DiscountState(List(DiscountState.ROUNDS) {
            DiscountQuestion("фонарь", stickerOld = 60, priceNow = 40, fairPrice = 50)
        })
        repeat(DiscountState.ROUNDS) { state = state.answer(believedReal = true).next() }
        assertTrue(state.finished)
        assertNotNull(DeedGameScore.fromDiscount(state))
    }

    @Test
    fun unfinishedGame_producesNoScore() {
        assertNull(DeedGameScore.fromDiscount(DiscountState.create().answer(believedReal = true)))
        assertFalse(DiscountState.create().finished)
    }
}
