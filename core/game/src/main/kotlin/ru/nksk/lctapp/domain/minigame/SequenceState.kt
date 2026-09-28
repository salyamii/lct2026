package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/**
 * Чистое состояние игры «Сигналы башни»: в раунде башня показывает
 * последовательность из [sequence.size] вспышек, игрок повторяет её.
 * Верный раунд увеличивает [correct]; ошибка завершает раунд без очка.
 * Финал после [ROUNDS] раундов. Все переходы возвращают новое состояние.
 */
data class SequenceState(
    val sequence: List<Int>,
    val round: Int = 0,
    val correct: Int = 0,
    val position: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    /** Текущая длина последовательности, которую надо повторить. */
    val requiredLength: Int get() = sequence.size

    /** Финал, когда пройдены все раунды. */
    val finished: Boolean get() = round >= ROUNDS

    /**
     * Нажатие на сигнал. Пока показан результат раунда, нажатия игнорируются —
     * UI должен сначала вызвать [next].
     */
    fun tap(signal: Int): SequenceState {
        if (finished || lastCorrect != null) return this
        if (signal !in SIGNALS.indices) return this
        val matched = sequence.getOrNull(position) == signal
        if (!matched) {
            return copy(round = round + 1, lastCorrect = false, position = 0)
        }
        return if (position + 1 == requiredLength) {
            copy(round = round + 1, correct = correct + 1, lastCorrect = true, position = 0)
        } else {
            copy(position = position + 1)
        }
    }

    /** Переход к следующему раунду после показа результата; подготавливает новую последовательность. */
    fun next(random: Random = Random.Default): SequenceState {
        if (finished || lastCorrect == null) return this
        val length = round + FIRST_ROUND_LENGTH
        return copy(
            sequence = List(length) { random.nextInt(SIGNALS.size) },
            lastCorrect = null,
            position = 0,
        )
    }

    companion object {
        const val ROUNDS = 5
        const val FIRST_ROUND_LENGTH = 3
        const val MAX_ROUND_LENGTH = ROUNDS - 1 + FIRST_ROUND_LENGTH

        /** Число различных сигналов башни. */
        const val SIGNAL_COUNT = 4

        val SIGNALS: List<Int> = List(SIGNAL_COUNT) { it }

        /** Готовит партию: первый раунд из [FIRST_ROUND_LENGTH] вспышек. */
        fun create(random: Random = Random.Default): SequenceState = SequenceState(
            sequence = List(FIRST_ROUND_LENGTH) { random.nextInt(SIGNAL_COUNT) },
        )
    }
}
