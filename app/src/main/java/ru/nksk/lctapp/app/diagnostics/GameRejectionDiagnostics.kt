package ru.nksk.lctapp.app.diagnostics

import ru.nksk.lctapp.domain.engine.BlockReason
import ru.nksk.lctapp.domain.engine.RejectedGameCommand

/** Keep content failures visible after navigation and ordinary care/budget guards. */
internal fun RejectedGameCommand.diagnosticFields(): Map<String, String> {
    if (reason !is BlockReason.InvalidContent && reason != BlockReason.MissingCarriedLore) return emptyMap()
    return linkedMapOf(
        "last_rejected_action" to command.diagnosticAction(),
        "last_rejected_origin" to origin.name,
        "last_rejected_reason" to if (reason is BlockReason.InvalidContent) "InvalidContent" else "MissingCarriedLore",
        "last_rejected_detail" to ((reason as? BlockReason.InvalidContent)?.detail?.safeContentDetail() ?: "carried_plan_mismatch"),
        "last_rejected_exception" to (failure?.exception ?: "domain_guard"),
    ).apply {
        repeat(3) { index -> put("last_rejected_source_$index", failure?.frames?.getOrNull(index) ?: "none") }
    }
}

/** Exception text may contain open content IDs. Only fixed authored checks enter the report. */
private fun String.safeContentDetail(): String = when {
    startsWith("Unknown event:") -> "unknown_event (id omitted)"
    startsWith("Missing authored event policy:") -> "missing_event_policy (id omitted)"
    startsWith("Unknown story day:") -> "unknown_story_day (id omitted)"
    this in safeContentMessages -> this
    else -> "other_content_invariant (detail omitted)"
}

private val safeContentMessages = setOf(
    "Failed requirement.",
    "Invalid authored content",
    "Invalid economy operation",
    "INVALID_ALLOCATION",
    "FORBIDDEN_COST",
    "Numeric overflow",
    "Saved engine uses a different rules version",
    "Engine occurrence and story active event must agree",
    "Existing story progress needs an explicit engine adoption policy",
    "Provide an authored day plan; only a ready chapter goal permits a short story plan",
    "Chapter changes require a completed final event",
    "A full day needs an unopened occurrence to defer the introduction's extra slot",
    "Unknown chapter",
    "Item removal needs an explicit ownership policy",
    "Completed projects follow the chronological order of their decisions",
)
