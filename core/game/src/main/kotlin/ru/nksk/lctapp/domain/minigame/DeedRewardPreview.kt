package ru.nksk.lctapp.domain.minigame

/**
 * Best final reward still possible if the remaining attempts are correct.
 * This is a read-only projection, not a completed score or a payment command.
 */
class DeedRewardPreview private constructor(
    val mistakes: Int,
    private val correct: Int,
    private val attempts: Int,
) {
    init {
        require(mistakes >= 0)
        require(attempts > 0 && correct in 0..attempts)
    }

    fun reward(maximum: Long): Long = deedReward(maximum, correct, attempts)

    /** The displayed coin amount as a share of the authored maximum, including the one-coin floor. */
    fun fraction(maximum: Long): Float {
        val remaining = reward(maximum)
        return if (maximum == 0L) 0f else (remaining.toDouble() / maximum).toFloat()
    }

    companion object {
        fun fromMemory(state: MemoryState): DeedRewardPreview {
            // Recall mistakes are recorded once, on the second tap. Blind discovery is free.
            val mistakes = state.recallMistakes
            return DeedRewardPreview(mistakes, MemoryState.PAIRS, MemoryState.PAIRS + mistakes)
        }

        fun fromComparison(state: PriceQuizState): DeedRewardPreview {
            // The current question advances only after its answer feedback is dismissed.
            val answered = state.current + if (state.lastCorrect != null) 1 else 0
            require(answered in 0..PriceQuizState.QUESTION_COUNT)
            require(state.correctAnswers in 0..answered)
            val mistakes = answered - state.correctAnswers
            return DeedRewardPreview(mistakes, PriceQuizState.QUESTION_COUNT - mistakes,
                PriceQuizState.QUESTION_COUNT)
        }

        fun fromPrecision(state: TargetStopState): DeedRewardPreview {
            // Precision already increments round while the hit/miss feedback is visible.
            require(state.round in 0..TargetStopState.ROUNDS)
            require(state.hits in 0..state.round)
            val mistakes = state.round - state.hits
            return DeedRewardPreview(mistakes, TargetStopState.ROUNDS - mistakes, TargetStopState.ROUNDS)
        }
    }
}
