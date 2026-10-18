package ru.nksk.lctapp.domain.minigame

import ru.nksk.lctapp.domain.minigame.chapterone.*

/** Supported mechanics, not a closed list of authored deeds or content IDs. */
enum class DeedGameKind {
    MEMORY, COMPARISON, PRECISION, LIGHTS, SEQUENCE, PIPES, DIFFERENCES, STACKING,
    TELESCOPE, MARK, WHEEL, STRAP, SIGNAL, ROUTE_MAP, BOAT,
    NEED_WANT, REAL_DISCOUNT, COIN_BALANCE, FINANCIAL_TRAP, EXTRA_PURCHASE,
}

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
            DeedGameKind.SEQUENCE -> SequenceState.isSupportedRoundLimit(attempts)
            DeedGameKind.PIPES -> attempts == 1 && correct == 1
            DeedGameKind.DIFFERENCES -> correct == DifferencesState.DIFF_COUNT && attempts >= DifferencesState.DIFF_COUNT
            DeedGameKind.STACKING -> attempts == StackingState.ROUNDS
            DeedGameKind.NEED_WANT -> attempts == PracticeGames.needs().questions.size
            DeedGameKind.REAL_DISCOUNT -> attempts == PracticeGames.discounts().questions.size
            DeedGameKind.COIN_BALANCE -> attempts == CoinBalanceState.create().rounds.size
            DeedGameKind.FINANCIAL_TRAP -> attempts == PracticeGames.traps().questions.size
            DeedGameKind.EXTRA_PURCHASE -> attempts == PracticeGames.extraPurchases().questions.size
            DeedGameKind.TELESCOPE, DeedGameKind.MARK, DeedGameKind.WHEEL,
            DeedGameKind.STRAP, DeedGameKind.SIGNAL, DeedGameKind.ROUTE_MAP, DeedGameKind.BOAT -> correct == 1 && attempts == 1
        }) { "Invalid completed mini-game result" }
    }
    /** Completed paid work earns at least one coin; a zero authored reward stays zero. */
    fun reward(maximum: Long): Long = deedReward(maximum, correct, attempts)

    companion object {
        /** Only the complete, unchanged exercise set can settle its paid deed. */
        fun fromNeedWant(state: PracticeChoiceState<NeedWantQuestion>): DeedGameScore? =
            fromPractice(DeedGameKind.NEED_WANT, state, PracticeGames.needs())

        fun fromRealDiscount(state: PracticeChoiceState<DiscountQuestion>): DeedGameScore? =
            fromPractice(DeedGameKind.REAL_DISCOUNT, state, PracticeGames.discounts())

        fun fromFinancialTrap(state: PracticeChoiceState<FinancialTrapQuestion>): DeedGameScore? =
            fromPractice(DeedGameKind.FINANCIAL_TRAP, state, PracticeGames.traps())

        fun fromExtraPurchase(state: PracticeChoiceState<ExtraPurchaseQuestion>): DeedGameScore? =
            fromPractice(DeedGameKind.EXTRA_PURCHASE, state, PracticeGames.extraPurchases())

        private fun <Q : PracticeQuestion> fromPractice(kind: DeedGameKind,
            state: PracticeChoiceState<Q>, original: PracticeChoiceState<Q>): DeedGameScore? =
            if (state.questions == original.questions && state.finished)
                DeedGameScore(kind, state.correctAnswers, state.questions.size) else null

        fun fromCoinBalance(state: CoinBalanceState): DeedGameScore? =
            if (state.rounds == CoinBalanceState.create().rounds && state.finished && state.started &&
                state.verdict == null && state.removed.isEmpty() && !state.madeMistake)
                DeedGameScore(DeedGameKind.COIN_BALANCE, state.correctAnswers, state.rounds.size) else null

        fun fromBoat(state: BoatJourney): DeedGameScore? =
            if (state.phase == BoatPhase.ARRIVED && state.plan != null && state.distance == 1f &&
                state.x.isFinite() && state.x in 0.18f..0.82f && state.targetX.isFinite() &&
                state.targetX in 0.18f..0.82f && state.activeRock == null && state.knotPoints in 0..3 &&
                state.touchedRocks.all { it in BoatJourney.rocks.indices } &&
                state.paidRepairs >= 0 && state.manualRepairs >= 0 &&
                state.paidRepairs + state.manualRepairs == state.touchedRocks.size &&
                state.practiceCoinsSpent in 0..BoatJourney.REPAIR_PRICE
            ) DeedGameScore(DeedGameKind.BOAT, 1, 1) else null

        fun fromTelescope(state: TelescopeAssembly): DeedGameScore? =
            if (state.completed && state.assembled && state.inFocus && state.selectedPart == null &&
                state.focus.isFinite() && state.focus in 0f..1f && state.selectedFastener in 0..1 &&
                state.firstFastener == TelescopeAssembly.MOUNT_TURN &&
                state.secondFastener == TelescopeAssembly.MOUNT_TURN &&
                state.eyepieceTurn == TelescopeAssembly.EYEPIECE_TURN
            ) DeedGameScore(DeedGameKind.TELESCOPE, 1, 1) else null

        fun fromMark(state: MarkState): DeedGameScore? =
            if (state.complete && state.cleaned == MarkGame.dust.indices.toSet() && state.point == 0
            ) DeedGameScore(DeedGameKind.MARK, 1, 1) else null

        fun fromWheel(state: WheelRepair): DeedGameScore? =
            if (state.completed && state.selectedSection in 0 until WheelRepair.SECTION_COUNT && state.angle.isFinite()
            ) DeedGameScore(DeedGameKind.WHEEL, 1, 1) else null

        fun fromStrap(state: StrapRepair): DeedGameScore? =
            if (state.phase == StrapPhase.DONE && state.aligned &&
                state.stitchedPoints == StrapRepair.STITCH_COUNT && state.knotTightened
            ) DeedGameScore(DeedGameKind.STRAP, 1, 1) else null

        fun fromSignal(state: SignalState): DeedGameScore? =
            if (state.complete && state.checked && state.entered == SignalGame.code
            ) DeedGameScore(DeedGameKind.SIGNAL, 1, 1) else null

        fun fromRouteMap(state: StarChartState): DeedGameScore? =
            if (state.joined == RouteMapGame.edges.indices.toSet() && state.selected == null
            ) DeedGameScore(DeedGameKind.ROUTE_MAP, 1, 1) else null

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
            if (state.isValid && state.finished && state.lastCorrect != null
            ) DeedGameScore(DeedGameKind.SEQUENCE, state.correct, state.roundLimit) else null

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
