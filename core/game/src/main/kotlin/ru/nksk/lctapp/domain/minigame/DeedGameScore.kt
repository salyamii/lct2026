package ru.nksk.lctapp.domain.minigame

/** Supported mechanics, not a closed list of authored deeds or content IDs. */
enum class DeedGameKind { MEMORY, COMPARISON, PRECISION }

/** A completed round set; incomplete boards cannot be submitted for payment. */
@kotlinx.serialization.Serializable
class DeedGameScore private constructor(
    val kind: DeedGameKind,
    val correct: Int,
    val attempts: Int,
) {
    init {
        require(attempts > 0 && correct in 0..attempts)
        require(when (kind) {
            DeedGameKind.MEMORY -> correct == MemoryState.PAIRS && attempts >= MemoryState.PAIRS
            DeedGameKind.COMPARISON -> attempts == PriceQuizState.QUESTION_COUNT
            DeedGameKind.PRECISION -> attempts == TargetStopState.ROUNDS
        }) { "Invalid completed mini-game result" }
    }
    /** Completed paid work earns at least one coin; a zero authored reward stays zero. */
    fun reward(maximum: Long): Long = deedReward(maximum, correct, attempts)

    companion object {
        fun fromMemory(state: MemoryState): DeedGameScore? =
            if (state.won && state.faces.size == MemoryState.PAIRS * 2 &&
                state.faces.groupingBy { it }.eachCount().values.all { it == 2 } &&
                state.matched == state.faces.indices.toSet() && state.pending == null &&
                state.faceUp.isEmpty() && state.moves >= MemoryState.PAIRS &&
                state.recallMistakes in 0..(state.moves - MemoryState.PAIRS)
            ) DeedGameScore(DeedGameKind.MEMORY, MemoryState.PAIRS,
                MemoryState.PAIRS + state.recallMistakes) else null

        fun fromComparison(state: PriceQuizState): DeedGameScore? =
            if (state.questions.size == PriceQuizState.QUESTION_COUNT &&
                state.current == PriceQuizState.QUESTION_COUNT && state.lastCorrect == null &&
                state.correctAnswers in 0..PriceQuizState.QUESTION_COUNT
            ) DeedGameScore(DeedGameKind.COMPARISON, state.correctAnswers, PriceQuizState.QUESTION_COUNT) else null

        fun fromPrecision(state: TargetStopState): DeedGameScore? =
            if (state.round == TargetStopState.ROUNDS && state.lastHit != null &&
                state.hits in 0..TargetStopState.ROUNDS
            ) DeedGameScore(DeedGameKind.PRECISION, state.hits, TargetStopState.ROUNDS) else null
    }
}

/** Shared by completed scores and read-only previews; this calculation never awards coins. */
internal fun deedReward(maximum: Long, correct: Int, attempts: Int): Long {
    require(maximum >= 0)
    require(attempts > 0 && correct in 0..attempts)
    if (maximum == 0L) return 0
    // Split the multiplication to preserve exact rounding without overflowing a large maximum.
    return (maximum / attempts * correct + maximum % attempts * correct / attempts).coerceAtLeast(1)
}
