package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BargainStateTest {
    @Test
    fun create_generatesDistinctUnitPrices() {
        val state = BargainState.create(random = Random(7))
        assertEquals(BargainState.ROUNDS, state.questions.size)
        state.questions.forEach { question ->
            assertTrue(kotlin.math.abs(question.left.unitPrice - question.right.unitPrice) >= 3)
            assertTrue(question.left.unitPrice != question.right.unitPrice)
        }
    }

    @Test
    fun answer_cheaperUnitPrice_countsCorrect() {
        val state = BargainState(listOf(BargainQuestion(BargainOffer(2, 20), BargainOffer(3, 33))))
        val answered = state.answer(pickedLeft = true)
        assertEquals(true, answered.lastCorrect)
        assertEquals(1, answered.correctAnswers)
    }

    @Test
    fun answer_twiceWithoutNext_isIgnored() {
        val state = BargainState(listOf(BargainQuestion(BargainOffer(2, 20), BargainOffer(3, 33))))
        val answered = state.answer(pickedLeft = true)
        assertEquals(answered, answered.answer(pickedLeft = false))
    }

    @Test
    fun afterLastAnswer_gameFinishes() {
        var state: BargainState = BargainState(List(BargainState.ROUNDS) {
            BargainQuestion(BargainOffer(2, 20), BargainOffer(3, 33))
        })
        repeat(BargainState.ROUNDS) { state = state.answer(pickedLeft = true).next() }
        assertTrue(state.finished)
        assertEquals(BargainState.ROUNDS, state.correctAnswers)
        assertNotNull(DeedGameScore.fromBargain(state))
    }

    @Test
    fun unfinishedGame_producesNoScore() {
        val state = BargainState.create().answer(pickedLeft = true)
        assertNull(DeedGameScore.fromBargain(state))
        assertFalse(state.finished)
    }
}
