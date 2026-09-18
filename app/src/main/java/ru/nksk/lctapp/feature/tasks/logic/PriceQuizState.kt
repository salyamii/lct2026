package ru.nksk.lctapp.feature.tasks.logic

import kotlin.random.Random

/** Один вопрос викторины: суммы монет слева и справа, всегда разные. */
data class QuizQuestion(val leftAmount: Int, val rightAmount: Int) {
    val leftIsBigger: Boolean get() = leftAmount > rightAmount
}

/**
 * Чистое состояние викторины «Что дороже?»: [QUESTION_COUNT] вопросов,
 * за каждый верный ответ — награда. Финал после последнего вопроса.
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

    /** Ответ игрока: true — выбрана левая карточка. Фиксирует результат, ждёт [next]. */
    fun answer(pickedLeft: Boolean): PriceQuizState {
        if (finished || lastCorrect != null) return this
        val correct = question.leftIsBigger == pickedLeft
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

        /** Генерирует вопросы с заметной разницей сумм, чтобы выбор был однозначным. */
        fun create(random: Random = Random.Default): PriceQuizState {
            val questions = List(QUESTION_COUNT) {
                val left = random.nextInt(10, 90)
                var right = random.nextInt(10, 90)
                while (kotlin.math.abs(left - right) < 10) {
                    right = random.nextInt(10, 90)
                }
                QuizQuestion(leftAmount = left, rightAmount = right)
            }
            return PriceQuizState(questions = questions)
        }
    }
}
