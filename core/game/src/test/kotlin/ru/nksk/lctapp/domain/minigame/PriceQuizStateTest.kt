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
        val questions = (0..200).flatMap { seed ->
            val state = PriceQuizState.create(random = Random(seed))
            assertEquals(PriceQuizState.QUESTION_COUNT, state.questions.size)
            state.questions
        }
        questions.forEach { question ->
            assertTrue(question.leftAmount in 10..199)
            assertTrue(question.rightAmount in 10..199)
            assertTrue(question.leftAmount != question.rightAmount)
        }
        assertEquals(setOf(true, false), questions.map { it.pickLarger }.toSet())
        assertTrue(questions.any { kotlin.math.abs(it.leftAmount - it.rightAmount) == 1 })
        val amounts = questions.flatMap { listOf(it.leftAmount, it.rightAmount) }
        assertTrue(10 in amounts)
        assertTrue(199 in amounts)
        assertEquals(PriceQuizState.create(Random(42)), PriceQuizState.create(Random(42)))
    }

    @Test
    fun answer_checksBothTargetsOnEitherSideIncludingAdjacentPrices() {
        for (pickLarger in listOf(true, false)) {
            for ((left, right) in listOf(10 to 199, 199 to 10, 198 to 199, 199 to 198)) {
                val state = PriceQuizState(listOf(QuizQuestion(left, right, pickLarger)))
                val expectedLeft = if (pickLarger) left > right else left < right
                val correct = state.answer(expectedLeft)
                assertEquals(true, correct.lastCorrect)
                assertEquals(1, correct.correctAnswers)
                assertEquals(PriceQuizState.REWARD_PER_CORRECT, correct.reward)
                val wrong = state.answer(!expectedLeft)
                assertEquals(false, wrong.lastCorrect)
                assertEquals(0, wrong.correctAnswers)
                assertEquals(0, wrong.reward)
                assertEquals(correct, correct.answer(!expectedLeft))
                val finished = correct.next()
                assertTrue(finished.finished)
                assertEquals(finished, finished.answer(!expectedLeft).next())
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun equalPricesAreNotAValidQuestion() {
        QuizQuestion(100, 100, pickLarger = false)
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
