package ru.nksk.lctapp.domain.minigame.chapterone

/** These observations reveal authored clues; they never rate financial knowledge or motor skill. */
enum class RecordStudyKind { CARGO_JOURNAL, THREE_LENSES, LIGHT_ORDER }

data class RecordStudy(
    val kind: RecordStudyKind,
    val recorded: List<Int> = emptyList(),
    val selectedLens: Int? = null,
    val needsCorrection: Boolean = false,
) {
    val complete: Boolean get() = when (kind) {
        RecordStudyKind.CARGO_JOURNAL -> recorded == RecordStudyGame.cargoSteps
        RecordStudyKind.THREE_LENSES -> recorded.size == 3 && recorded.toSet() == setOf(0, 1, 2)
        RecordStudyKind.LIGHT_ORDER -> recorded == SignalGame.code.map { it.ordinal }
    }
}

object RecordStudyGame {
    // Crate in the ledger, then pier, bend, old bridge/post, and tower on its printed map.
    val cargoSteps = listOf(0, 1, 2, 3, 4)

    fun visit(state: RecordStudy, point: Int): RecordStudy {
        if (state.kind != RecordStudyKind.CARGO_JOURNAL || state.complete || point !in cargoSteps) return state
        if (point in state.recorded) return state
        return if (point == cargoSteps.getOrNull(state.recorded.size)) {
            state.copy(recorded = state.recorded + point, needsCorrection = false)
        } else state.copy(needsCorrection = true)
    }

    fun selectLens(state: RecordStudy, light: SignalLight): RecordStudy {
        if (state.kind != RecordStudyKind.THREE_LENSES || state.complete || light.ordinal in state.recorded) return state
        return state.copy(selectedLens = light.ordinal, needsCorrection = false)
    }

    fun placeLens(state: RecordStudy, socket: SignalLight): RecordStudy {
        if (state.kind != RecordStudyKind.THREE_LENSES || state.complete ||
            socket.ordinal in state.recorded || state.selectedLens == null) return state
        return if (state.selectedLens == socket.ordinal) {
            state.copy(recorded = state.recorded + socket.ordinal, selectedLens = null, needsCorrection = false)
        } else state.copy(needsCorrection = true)
    }

    fun stamp(state: RecordStudy, light: SignalLight): RecordStudy {
        if (state.kind != RecordStudyKind.LIGHT_ORDER || state.complete) return state
        return if (light == SignalGame.code.getOrNull(state.recorded.size)) {
            state.copy(recorded = state.recorded + light.ordinal, needsCorrection = false)
        } else state.copy(needsCorrection = true)
    }

    /** Reject corrupt/impossible saved progress instead of turning it into a completed clue. */
    fun restore(kind: RecordStudyKind, recorded: List<Int>, selectedLens: Int?): RecordStudy {
        val valid = when (kind) {
            RecordStudyKind.CARGO_JOURNAL -> recorded == cargoSteps.take(recorded.size) && recorded.size <= cargoSteps.size
            RecordStudyKind.THREE_LENSES -> recorded.size <= 3 && recorded.distinct() == recorded &&
                recorded.all { it in SignalLight.entries.indices }
            RecordStudyKind.LIGHT_ORDER -> recorded.size <= SignalGame.code.size &&
                recorded == SignalGame.code.take(recorded.size).map { it.ordinal }
        }
        if (!valid) return RecordStudy(kind)
        val selection = selectedLens?.takeIf {
            kind == RecordStudyKind.THREE_LENSES && it in SignalLight.entries.indices && it !in recorded
        }
        return RecordStudy(kind, recorded.toList(), selection)
    }
}
