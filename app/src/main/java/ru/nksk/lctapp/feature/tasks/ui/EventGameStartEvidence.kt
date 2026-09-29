package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.history.AuditType

internal data class EventGameStartEvidence(val context: DecisionContext?, val priorityOfferId: String?)

/** A played board does not imply the financial explanation was shown; only the matching receipt proves it. */
internal fun eventGameStartEvidence(entry: AuditEntry?, current: GameState,
    command: EngineCommand.CompleteStoryGame): EventGameStartEvidence? {
    if (entry?.type != AuditType.COMMAND) return null
    val start = entry.request?.command as? EngineCommand.StartStoryGame ?: return null
    if (start.occurrenceId != command.occurrenceId || start.choiceId != command.choiceId) return null
    // A pause, new choice, purchase, budget edit, meal or other revision invalidates the old presentation.
    if (entry.after != current) return null
    val request = checkNotNull(entry.request)
    val context = entry.context ?: request.context
    return EventGameStartEvidence(context, start.resourcePriorityOfferId)
}
