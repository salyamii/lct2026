package ru.nksk.lctapp.domain.timemachine

import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.game.GameState

enum class TimeMachineStatus { READY, COMPLETE, DIVERGED, LOCKED, UNAVAILABLE, INCOMPATIBLE_VERSION }

data class TimeMachineAvailability(
    val status: TimeMachineStatus,
    val decisions: List<TimeMachineDecision> = emptyList(),
    val reason: String? = null,
)

data class TimeMachineDecision(
    val entryId: String,
    val sequence: Long,
    val day: Int?,
    val title: String,
    val alternatives: List<TimeMachineAlternative>,
)

/** A null command means omitting an optional purchase/transfer, not performing an invented effect. */
data class TimeMachineAlternative(val id: String, val title: String, val command: EngineCommand?,
    /** Fixed authored consequences if the manual work is completed, without inventing a score. */
    val assumesCompletedWork: Boolean = false)
data class TimeMachineRequest(val entryId: String, val alternativeId: String, val throughSequence: Long? = null)

data class TimeMachineBranch(val state: GameState, val operations: List<LedgerEntry>) {
    val income: Long get() = total(LedgerKind.INCOME)
    val spent: Long get() = Math.addExact(total(LedgerKind.AVAILABLE_EXPENSE), total(LedgerKind.SAVINGS_EXPENSE))
    val deposited: Long get() = total(LedgerKind.DEPOSIT)
    val withdrawn: Long get() = total(LedgerKind.WITHDRAWAL)
    private fun total(kind: LedgerKind) = operations.filter { it.kind == kind }.fold(0L) { n, item -> Math.addExact(n, item.amount) }
}

data class TimeMachineResult(
    val status: TimeMachineStatus,
    val request: TimeMachineRequest,
    val simulationId: String? = null,
    val resultHash: String? = null,
    val runId: String? = null,
    val reachedSequence: Long? = null,
    val requestedSequence: Long? = null,
    val baseline: TimeMachineBranch? = null,
    val alternative: TimeMachineBranch? = null,
    val reason: String? = null,
)

enum class TimeMachineQuizKind { CAUSE, LEDGER }
data class TimeMachineQuizOption(val id: String, val text: String)
data class TimeMachineQuiz(
    val id: String,
    val simulationId: String,
    val resultHash: String,
    val kind: TimeMachineQuizKind,
    val prompt: String,
    val options: List<TimeMachineQuizOption>,
    val answerAlreadyShown: Boolean,
)

data class TimeMachineQuizAnswer(
    val quizId: String,
    val optionId: String,
    val correct: Boolean,
    val explanation: String,
    val attempt: Int,
    val alreadyRecorded: Boolean = false,
    val submissionId: String? = null,
)
