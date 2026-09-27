package ru.nksk.lctapp.domain.history

/** A learning fact may reference only the exact source history used for its calculation. */
data class HistorySourceGuard(
    val runId: String,
    val fromSequence: Long,
    val throughSequence: Long,
    val signature: String,
) {
    init {
        require(runId.isNotBlank() && fromSequence > 0 && throughSequence >= fromSequence)
        require(signature.matches(Regex("[0-9a-f]{64}")))
    }

    /** Repositories must call this inside the same write transaction that appends the facts. */
    fun requireMatches(history: List<AuditEntry>) {
        require(history.lastOrNull()?.runId == runId) { "The learning source belongs to another playthrough" }
        val records = history.filter { it.sequence in fromSequence..throughSequence }
        require(records.firstOrNull()?.sequence == fromSequence && records.lastOrNull()?.sequence == throughSequence &&
            records.all { it.runId == runId } && signatureOf(records) == signature) {
            "The source history changed; reopen the comparison"
        }
    }

    companion object {
        fun capture(runId: String, fromSequence: Long, throughSequence: Long, history: List<AuditEntry>): HistorySourceGuard {
            val records = history.filter { it.sequence in fromSequence..throughSequence }
            require(records.firstOrNull()?.sequence == fromSequence && records.lastOrNull()?.sequence == throughSequence &&
                records.all { it.runId == runId }) { "The source boundaries are unavailable" }
            return HistorySourceGuard(runId, fromSequence, throughSequence, signatureOf(records))
        }

        private fun signatureOf(records: List<AuditEntry>) =
            HistoryCodec.sha256(records.joinToString("\n") { HistoryCodec.encode(it) })
    }
}
