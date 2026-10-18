package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** The target belongs to the question; legacy questions ask for the larger amount. */
data class QuizQuestion(val leftAmount: Int, val rightAmount: Int, val pickLarger: Boolean = true) {
    init { require(leftAmount > 0 && rightAmount > 0 && leftAmount != rightAmount) }
    val leftIsCorrect: Boolean get() = if (pickLarger) leftAmount > rightAmount else leftAmount < rightAmount
}

/**
 * Чистое состояние сравнения цен: [QUESTION_COUNT] вопросов «дороже» / «дешевле»,
 * за каждый верный ответ - награда. Финал после последнего вопроса.
 */
data class PriceQuizState(
    val questions: List<QuizQuestion>,
    val current: Int = 0,
    val correctAnswers: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    val question: QuizQuestion get() = questions[current]

    /** Верных ответов дано; финал, когда отвечены все вопросы. */
    val finished: Boolean get() = current >= questions.size

    /** Награда в монетах за текущий результат. */
    val reward: Int get() = correctAnswers * REWARD_PER_CORRECT

    /** Ответ игрока: true - выбрана левая карточка. Фиксирует результат, ждёт [next]. */
    fun answer(pickedLeft: Boolean): PriceQuizState {
        if (finished || lastCorrect != null) return this
        val correct = question.leftIsCorrect == pickedLeft
        return copy(
            correctAnswers = if (correct) correctAnswers + 1 else correctAnswers,
            lastCorrect = correct,
        )
    }

    /** Переход к следующему вопросу после показа результата. */
    fun next(): PriceQuizState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, lastCorrect = null)
    }

    companion object {
        const val QUESTION_COUNT = 5
        const val REWARD_PER_CORRECT = 2
        const val MAX_REWARD = QUESTION_COUNT * REWARD_PER_CORRECT

        /** Independent random targets and distinct prices in [10, 200), including close prices. */
        fun create(random: Random = Random.Default): PriceQuizState {
            val questions = List(QUESTION_COUNT) {
                val left = random.nextInt(10, 200)
                // Draw uniformly from the other 189 prices, without retrying equal values.
                val other = random.nextInt(10, 199)
                val right = if (other >= left) other + 1 else other
                QuizQuestion(leftAmount = left, rightAmount = right, pickLarger = random.nextBoolean())
            }
            return PriceQuizState(questions = questions)
        }
    }
}
