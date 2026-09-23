package ru.nksk.lctapp.data.game.local

import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.DeedOffer
import ru.nksk.lctapp.domain.engine.EngineState
import ru.nksk.lctapp.domain.engine.EventOccurrence
import ru.nksk.lctapp.domain.engine.EventOrigin
import ru.nksk.lctapp.domain.engine.EventStatus

internal fun EngineState.toEntity() = EngineStateEntity(
    CURRENT_GAME_ID, rulesId, revision, day, phase.name, steps, energy, ateToday, nextMorningEnergy, openingBalance, openingEnergy, balanceAdjustment,
)

/** These enum names are v2 wire codes. A future rename requires an explicit compatible mapping. */
internal fun EngineStateEntity.toDomain(events: List<EngineEventEntity>, deeds: List<EngineDeedEntity>, journal: List<DayJournalEntity> = emptyList()) = EngineState(
    rulesId, revision, day, DayPhase.valueOf(phase), steps, energy, ateToday, nextMorningEnergy, openingBalance,
    events.map {
        require((it.eventId == null) != (it.deedOfferId == null)) { "Occurrence must reference exactly one event or deed" }
        val eventId = it.eventId ?: requireNotNull(deeds.find { deed -> deed.id == it.deedOfferId }).eventId
        EventOccurrence(it.id, eventId, if (it.deedOfferId == null) EventOrigin.SCHEDULE else EventOrigin.DEED, EventStatus.valueOf(it.status), it.deedOfferId)
    },
    deeds.map { DeedOffer(it.id, it.eventId, it.expiresDay, it.completed) },
    openingEnergy,
    journal.map { it.toDomain() },
    balanceAdjustment = balanceAdjustment,
)
