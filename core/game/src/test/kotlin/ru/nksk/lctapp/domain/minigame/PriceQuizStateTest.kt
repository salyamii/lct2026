package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceQuizStateTest {
    @Test
    fun create_generatesFiveDistinctAmountQuestions() {
        val state = PriceQuizState.create(random = Random(42))
        assertEquals(PriceQuizState.QUESTION_COUNT, state.questions.size)
        state.questions.forEach { question ->
            assertTrue(kotlin.math.abs(question.leftAmount - question.rightAmount) >= 10)
        }
    }

    @Test
    fun answer_leftWhenLeftBigger_countsCorrect() {
        val state = PriceQuizState(questions = listOf(QuizQuestion(50, 20)))
        val answered = state.answer(pickedLeft = true)
        assertEquals(true, answered.lastCorrect)
        assertEquals(1, answered.correctAnswers)
        assertEquals(2, answered.reward)
    }

    @Test
    fun answer_wrong_side_countsIncorrect() {
        val state = PriceQuizState(questions = listOf(QuizQuestion(50, 20)))
        val answered = state.answer(pickedLeft = false)
        assertEquals(false, answered.lastCorrect)
        assertEquals(0, answered.correctAnswers)
    }

    @Test
    fun answer_twiceWithoutNext_isIgnored() {
        val state = PriceQuizState(questions = listOf(QuizQuestion(50, 20), QuizQuestion(10, 30)))
        val answered = state.answer(pickedLeft = true)
        assertEquals(answered, answered.answer(pickedLeft = false))
    }

    @Test
    fun next_advancesToFollowingQuestion() {
        val state = PriceQuizState(questions = listOf(QuizQuestion(50, 20), QuizQuestion(10, 30)))
        val answered = state.answer(pickedLeft = true).next()
        assertEquals(1, answered.current)
        assertNull(answered.lastCorrect)
    }

    @Test
    fun afterLastAnswer_quizFinishes() {
        val state = PriceQuizState(questions = listOf(QuizQuestion(50, 20)))
        val answered = state.answer(pickedLeft = true).next()
        assertTrue(answered.finished)
    }

    @Test
    fun reward_isTwoPerCorrectAnswer() {
        val questions = List(PriceQuizState.QUESTION_COUNT) { QuizQuestion(50, 20) }
        var state = PriceQuizState(questions = questions)
        repeat(3) {
            state = state.answer(pickedLeft = true).next()
        }
        assertEquals(3, state.correctAnswers)
        assertEquals(6, state.reward)
        assertFalse(state.finished)
    }
}
