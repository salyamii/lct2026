package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

/**
 * Adopt a declared content revision without replaying entry effects or rewriting past choices.
 * Old definitions remain in the catalog. Deed offers need their own migration and are excluded.
 */
internal class EventOccurrenceReplacements(catalog: GameCatalog) {
    private val replacements = catalog.eventReplacements.toMap()

    init {
        val events = catalog.content.events.associateBy { it.id }
        for ((oldId, newId) in replacements) {
            require(oldId != newId && newId !in replacements) {
                "Event replacements must point directly to the latest definition without chains or cycles"
            }
            val old = requireNotNull(events[oldId]) { "Missing replaced event: $oldId" }
            val next = requireNotNull(events[newId]) { "Missing replacement event: $newId" }
            require(old.type != EventType.EARNING && old.copy(
                id = newId, title = next.title, description = next.description,
            ) == next) { "An event replacement must preserve its type, requirements and entry effects" }
            val oldPolicy = requireNotNull(catalog.policies[oldId]) { "Missing replaced event policy: $oldId" }
            val nextPolicy = requireNotNull(catalog.policies[newId]) { "Missing replacement event policy: $newId" }
            require(oldPolicy.startEffectsTiming == nextPolicy.startEffectsTiming &&
                oldPolicy.chapterEntryDayId == nextPolicy.chapterEntryDayId &&
                oldPolicy.deedGameKind == null && nextPolicy.deedGameKind == null) {
                "An event replacement cannot change entry timing or migrate deed offers"
            }
            fun entryItems(id: String) = catalog.content.eventItemEffects.filter { it.eventId == id }
                .sortedBy { it.position }.map { Triple(it.position, it.itemId, it.operation) }
            require(entryItems(oldId) == entryItems(newId)) {
                "An event replacement must preserve ordered entry item effects"
            }
        }
    }

    suspend fun synchronize(games: GameRepository) {
        if (replacements.isNotEmpty()) games.update(::apply)
    }

    /** Derives every change from the latest aggregate supplied by the repository transaction. */
    fun apply(current: GameState): GameState {
        val day = current.engine ?: return current
        val decisionIds = current.story.decisions.mapTo(hashSetOf()) { it.id }
        val events = day.events.map { occurrence ->
            val newId = replacements[occurrence.eventId]
            if (newId == null || occurrence.origin != EventOrigin.SCHEDULE ||
                occurrence.status == EventStatus.RESULT || occurrence.status == EventStatus.COMPLETED ||
                "${occurrence.id}:decision" in decisionIds) occurrence
            else occurrence.copy(eventId = newId)
        }
        if (events == day.events) return current
        val updatedDay = day.copy(events = events, revision = Math.addExact(day.revision, 1L))
        return current.copy(
            engine = updatedDay,
            story = current.story.copy(activeEventId = updatedDay.currentEvent?.eventId),
        )
    }
}
