package ru.nksk.lctapp.domain.minigame

/** Supported mechanics, not a closed list of authored deeds or content IDs. */
enum class DeedGameKind { MEMORY, COMPARISON, PRECISION, LIGHTS, SEQUENCE, PIPES, DIFFERENCES, STACKING }

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
            DeedGameKind.LIGHTS -> attempts == 1 && correct == 1
            DeedGameKind.SEQUENCE -> attempts == SequenceState.ROUNDS
            DeedGameKind.PIPES -> attempts == 1 && correct == 1
            DeedGameKind.DIFFERENCES -> correct == DifferencesState.DIFF_COUNT && attempts >= DifferencesState.DIFF_COUNT
            DeedGameKind.STACKING -> attempts == StackingState.ROUNDS
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

        fun fromLights(state: LightsState): DeedGameScore? =
            if (state.won && state.grid.size == LightsState.SIZE * LightsState.SIZE &&
                state.grid.toSet() == setOf(false) && state.moves >= 1
            ) DeedGameScore(DeedGameKind.LIGHTS, 1, 1) else null

        fun fromSequence(state: SequenceState): DeedGameScore? =
            if (state.finished && state.round == SequenceState.ROUNDS && state.lastCorrect != null &&
                state.correct in 0..SequenceState.ROUNDS &&
                state.sequence.size in SequenceState.FIRST_ROUND_LENGTH..SequenceState.MAX_ROUND_LENGTH &&
                state.sequence.all { it in SequenceState.SIGNALS.indices }
            ) DeedGameScore(DeedGameKind.SEQUENCE, state.correct, SequenceState.ROUNDS) else null

        fun fromPipes(state: PipesState): DeedGameScore? =
            if (state.won && PipesState.isLayoutValid(state.endpoints) &&
                state.activeColor == null && state.activePath.isEmpty() &&
                state.endpoints.all { connection ->
                    val path = state.paths[connection.color]
                    path != null && path.isValidPipePath(connection)
                }
            ) DeedGameScore(DeedGameKind.PIPES, 1, 1) else null

        fun fromDifferences(state: DifferencesState): DeedGameScore? =
            if (state.won && state.top.size == DifferencesState.CELLS && state.bottom.size == DifferencesState.CELLS &&
                state.differences.size == DifferencesState.DIFF_COUNT &&
                state.found == state.differences.toSet() && state.taps >= DifferencesState.DIFF_COUNT
            ) DeedGameScore(DeedGameKind.DIFFERENCES, DifferencesState.DIFF_COUNT, state.taps) else null

        fun fromStacking(state: StackingState): DeedGameScore? =
            if (state.finished && state.placed in 0..StackingState.ROUNDS &&
                state.locked.size == state.placed &&
                state.locked.all { it.width > 0 && it.x >= 0 && it.x + it.width <= StackingState.SPACE } &&
                state.blockWidth > 0
            ) DeedGameScore(DeedGameKind.STACKING, state.placed, StackingState.ROUNDS) else null

        private fun List<Int>.isValidPipePath(connection: PipeEndpoints): Boolean {
            // Either endpoint may start the same continuous path.
            val forward = firstOrNull() == connection.first && lastOrNull() == connection.second
            val backward = firstOrNull() == connection.second && lastOrNull() == connection.first
            if (!forward && !backward) return false
            if (size != distinct().size) return false
            return zipWithNext().all { (a, b) ->
                val distance = kotlin.math.abs(a / PipesState.SIZE - b / PipesState.SIZE) +
                    kotlin.math.abs(a % PipesState.SIZE - b % PipesState.SIZE)
                distance == 1
            }
        }
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
