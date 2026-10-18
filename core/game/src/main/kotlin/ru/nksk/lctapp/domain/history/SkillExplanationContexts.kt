package ru.nksk.lctapp.domain.history

import ru.nksk.lctapp.domain.analytics.AnalyticsFact
import ru.nksk.lctapp.domain.analytics.AnalyticsMode
import ru.nksk.lctapp.domain.analytics.EpisodeCompletion
import ru.nksk.lctapp.domain.analytics.FactDetail
import ru.nksk.lctapp.domain.analytics.LearningContext
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.analytics.ObservationEligibility
import ru.nksk.lctapp.domain.analytics.ObservationOutcome
import ru.nksk.lctapp.domain.analytics.ObservationReason
import ru.nksk.lctapp.domain.analytics.ReserveApplication
import ru.nksk.lctapp.domain.analytics.SkillEventContext
import ru.nksk.lctapp.domain.analytics.SkillId
import ru.nksk.lctapp.domain.analytics.SkillObservation
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.pet.renderPetText

/** Read-only names for actual recorded actions; no worlds or history list are retained. */
class SkillExplanationContexts private constructor(
    private val facts: Map<Key, FactContext?>,
    private val expenses: Map<Key, ExpenseContext?>,
) {
    fun contextFor(observation: SkillObservation): SkillEventContext? {
        if (observation.ruleVersion != 1 || observation.completion != EpisodeCompletion.COMPLETE ||
            observation.eligibility != ObservationEligibility.ELIGIBLE || observation.sourceEventIds.isEmpty()
        ) return null
        val sources = observation.sourceEventIds.distinct().map { id ->
            facts[Key(observation.gameRunId, id)]?.takeIf { it.episodeId == observation.episodeId } ?: return null
        }
        if (observation.skill == SkillId.BUILD_EMERGENCY_FUND) {
            if (observation.outcome != ObservationOutcome.SUPPORTED || observation.reason != ObservationReason.RESERVE_USED) return null
            val applications = sources.mapNotNull { it.reserveApplications }.flatten()
            if (applications.isEmpty()) return null
            val contexts = applications.map { application ->
                expenses[Key(observation.gameRunId, application.expenseOperationId)]
                    ?.takeIf { application.amount <= it.amount }?.context ?: return null
            }
            return contexts.distinct().singleOrNull()
        }
        return sources.map { it.direct ?: return null }.distinct().singleOrNull()
    }

    private data class Key(val runId: String, val id: String)
    private data class FactContext(
        val episodeId: String,
        val direct: SkillEventContext?,
        val reserveApplications: List<ReserveApplication>?,
    )
    private data class ExpenseContext(val amount: Long, val context: SkillEventContext)

    companion object {
        fun from(history: List<AuditEntry>, facts: List<AnalyticsFact>, content: StoryContent): SkillExplanationContexts {
            val incoming = facts.groupBy { Key(it.gameRunId, it.eventId) }.mapValues { (_, copies) -> copies.distinct().singleOrNull() }
            val events = content.events.groupBy { it.id }.mapValues { (_, copies) -> copies.singleOrNull() }
            val choices = content.choices.groupBy { it.id }.mapValues { (_, copies) -> copies.singleOrNull() }
            val direct = mutableMapOf<Key, SkillEventContext?>()
            val expenses = mutableMapOf<Key, ExpenseContext?>()

            for (entry in history) {
                val before = entry.before
                val after = entry.after
                val request = entry.request
                val day = after?.engine?.day
                val committed = entry.type == AuditType.COMMAND && request?.demoMode == false &&
                    before != null && after != null && day != null &&
                    runCatching { CanonicalLedger.validate(before, after, entry.operations) }.isSuccess
                val oldJournal = before?.engine?.journal.orEmpty().mapTo(hashSetOf()) { it.id }
                val journal = after?.engine?.journal.orEmpty().filter { it.id !in oldJournal }

                fun context(eventId: String?): SkillEventContext? {
                    if (!committed || eventId == null) return null
                    val title = events[eventId]?.title ?: return null
                    val rendered = renderPetText(title, checkNotNull(after).pet.name).trim()
                    if (rendered.isBlank() || '\u0000' in rendered) return null
                    return SkillEventContext(checkNotNull(day), rendered)
                }
                fun journalEvent(kind: DayJournalKind, source: String): String? = when (kind) {
                    DayJournalKind.EVENT_START -> source
                    DayJournalKind.EVENT_CHOICE, DayJournalKind.DEED -> choices[source]?.eventId
                    else -> null
                }
                val occurrenceId = when (val command = request?.command) {
                    is EngineCommand.Choose -> command.occurrenceId
                    is EngineCommand.CompleteEvent -> command.occurrenceId
                    is EngineCommand.CompleteStoryGame -> command.occurrenceId
                    is EngineCommand.CompleteDeed -> command.occurrenceId
                    is EngineCommand.AcceptDeedProposal -> command.occurrenceId
                    else -> null
                }
                val occurrenceEvents = if (occurrenceId == null) emptyList() else
                    (before?.engine?.events.orEmpty() + after?.engine?.events.orEmpty())
                        .filter { it.id == occurrenceId }.map { it.eventId }.distinct()
                val eventJournal = journal.filter { it.kind in setOf(
                    DayJournalKind.EVENT_START, DayJournalKind.EVENT_CHOICE, DayJournalKind.DEED) }
                val journalEvents = eventJournal.map { journalEvent(it.kind, it.sourceId) }
                val explicitChoice = when (val command = request?.command) {
                    is EngineCommand.Choose -> command.choiceId
                    is EngineCommand.CompleteEvent -> command.choiceId
                    is EngineCommand.CompleteStoryGame -> command.choiceId
                    else -> null
                }
                val knownIdentity = journalEvents.all { it != null && events[it] != null } &&
                    (occurrenceId == null || occurrenceEvents.size == 1) &&
                    (explicitChoice == null || choices[explicitChoice]?.eventId in occurrenceEvents)
                val eventIds = journalEvents.filterNotNull() + occurrenceEvents
                val eventContext = eventIds.distinct().singleOrNull()?.takeIf { knownIdentity }?.let(::context)

                for (source in entry.facts) {
                    val key = Key(entry.runId, source.eventId)
                    val matching = incoming[key]?.sameRecordedFact(source) == true
                    val knownDay = source.context.day
                    val value = eventContext?.takeIf { matching && source.isRealGameFact() &&
                        source.actionId == request?.id && (knownDay == null || knownDay == it.day) }
                    direct.putUnique(key, value)
                }
                for (operation in entry.operations) {
                    val key = Key(entry.runId, operation.operationId)
                    val source = entry.facts.filter { fact ->
                        fact.isRealGameFact() && (fact.detail as? FactDetail.UnexpectedExpense)?.let {
                            it.operationId == operation.operationId && it.amount == operation.amount && !it.previouslyDisclosed
                        } == true
                    }.singleOrNull()
                    val receipt = journal.singleOrNull { it.id == operation.operationId }
                    val receiptEventId = receipt?.let { journalEvent(it.kind, it.sourceId) }
                    val resolved = context(receiptEventId)
                        ?.takeIf { occurrenceId == null || occurrenceEvents.singleOrNull() == receiptEventId }
                    val value = if (operation.kind == LedgerKind.AVAILABLE_EXPENSE && source != null &&
                        source.actionId == request?.id && incoming[Key(entry.runId, source.eventId)]?.sameRecordedFact(source) == true &&
                        receipt != null && receipt.moneyDelta < 0 && receipt.moneyDelta == -operation.amount &&
                        resolved != null && (source.context.day == null || source.context.day == resolved.day)
                    ) ExpenseContext(operation.amount, resolved) else null
                    expenses.putUnique(key, value)
                }
            }
            val references = incoming.mapValues { (key, fact) -> fact?.let {
                if (!it.isRealGameFact()) null else FactContext(it.episodeId, direct[key], it.reserveApplications())
            } }
            return SkillExplanationContexts(references, expenses)
        }

        private fun AnalyticsFact.isRealGameFact(): Boolean =
            schemaVersion == 1 && mode == AnalyticsMode.REAL && learningContext == LearningContext.GAME

        // Restored-world transport cursors differ from local journal numbering; identity and content do not.
        private fun AnalyticsFact.sameRecordedFact(other: AnalyticsFact): Boolean =
            copy(sequence = 0) == other.copy(sequence = 0)

        private fun AnalyticsFact.reserveApplications(): List<ReserveApplication>? {
            val reserve = detail as? FactDetail.ReserveDecision ?: return null
            if (!context.complete || !context.informationPresented || context.presentationId == null ||
                !reserve.intervalClosed || reserve.usedForUnexpectedExpense <= 0 || reserve.applications.isEmpty() ||
                reserve.applications.map { it.expenseOperationId }.distinct().size != reserve.applications.size
            ) return null
            val reconciled = runCatching {
                Math.addExact(reserve.remainingAmount, reserve.usedForUnexpectedExpense) == reserve.declaredAmount &&
                    reserve.applications.fold(0L) { total, item -> Math.addExact(total, item.amount) } == reserve.usedForUnexpectedExpense
            }.getOrDefault(false)
            return reserve.applications.takeIf { reconciled }
        }

        /** Repeated identities cannot silently select one of several recorded actions. */
        private fun <K, V> MutableMap<K, V?>.putUnique(key: K, value: V?) {
            if (containsKey(key)) this[key] = null else this[key] = value
        }
    }
}
