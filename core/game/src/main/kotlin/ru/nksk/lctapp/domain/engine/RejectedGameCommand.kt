package ru.nksk.lctapp.domain.engine

/** Transient diagnostics only. Neither the failure nor its stack is part of a saved game. */
data class RejectedGameCommand(
    val command: EngineCommand,
    val reason: BlockReason,
    val failure: ContentFailureSource? = null,
    val origin: RejectionOrigin = RejectionOrigin.COMMAND,
)

/** Availability checks are diagnostics, never attempted gameplay or financial evidence. */
enum class RejectionOrigin { COMMAND, GUARD_CHECK }

/** Code locations only: no exception messages, command payloads or game snapshots. */
data class ContentFailureSource(val exception: String, val frames: List<String>) {
    companion object {
        internal fun capture(error: Throwable): ContentFailureSource {
            val trace = error.stackTrace
            val firstDomain = trace.indexOfFirst { it.className.startsWith("ru.nksk.lctapp.domain.") }
            // R8 may rename the domain package. Preserve its original frames for mapping.txt.
            val first = if (firstDomain >= 0) firstDomain else 0
            return ContentFailureSource(error.javaClass.name, trace.drop(first).take(3).map { it.toString() })
        }
    }
}
