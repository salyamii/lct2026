package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState

/** Actual committed changes during the current day, not estimates from catalog rewards. */
@kotlinx.serialization.Serializable
data class DayJournalEntry(
    val id: String,
    val kind: DayJournalKind,
    val sourceId: String,
    val moneyDelta: Long,
    val energyDelta: Int = 0,
) {
    init { require(id.isNotBlank() && sourceId.isNotBlank()) }
}

enum class DayJournalKind { WEEKLY_INCOME, MEAL, ITEM_PURCHASE, ITEM_RECEIVED, EVENT_START, EVENT_CHOICE, DEED, PARENT_REWARD }

/** Called only after a successful transition, inside the same aggregate write transaction. */
internal fun recordDayChanges(before: GameState, after: GameState, request: EngineRequest, factory: EventFactory): GameState {
    val day = after.engine ?: return after
    val entries = mutableListOf<DayJournalEntry>()
    fun record(kind: DayJournalKind, source: String, money: Long, energy: Int = 0) {
        if (money != 0L || energy != 0 || kind == DayJournalKind.MEAL || kind == DayJournalKind.ITEM_RECEIVED) {
            entries += DayJournalEntry("${request.id}:journal:${entries.size}", kind, source, money, energy)
        }
    }
    val moneyDelta = Math.subtractExact(after.economy.balance, before.economy.balance)
    val beganDay = before.engine?.day != day.day
    fun openingEffects(): Long {
        val occurrence = day.currentEvent ?: return 0
        if (occurrence.status != EventStatus.ACTIVE ||
            before.engine?.events?.any { it.id == occurrence.id && it.status == EventStatus.PAUSED } == true) return 0
        val event = factory.event(occurrence.eventId)
        return if (factory.policy(event.id).startEffectsTiming == EffectTiming.OPEN) event.moneyDeltaOnStart else 0
    }
    if (beganDay) {
        val openingCost = openingEffects()
        record(DayJournalKind.WEEKLY_INCOME, day.rulesId, Math.subtractExact(moneyDelta, openingCost))
        day.currentEvent?.let { record(DayJournalKind.EVENT_START, it.eventId, openingCost) }
    } else when (val command = request.command) {
        is EngineCommand.Feed -> record(DayJournalKind.MEAL, command.mealId, moneyDelta,
            day.energy - checkNotNull(before.engine).energy)
        is EngineCommand.BuyGoalItem -> record(DayJournalKind.ITEM_PURCHASE, command.itemId, moneyDelta)
        EngineCommand.OpenNextEvent -> day.currentEvent?.let {
            record(DayJournalKind.EVENT_START, it.eventId, moneyDelta)
        }
        is EngineCommand.Choose, is EngineCommand.CompleteEvent, is EngineCommand.CompleteDeed, is EngineCommand.CompleteStoryGame -> {
            val occurrence = checkNotNull(before.engine?.currentEvent)
            val event = factory.event(occurrence.eventId)
            val startCost = if (factory.policy(event.id).startEffectsTiming == EffectTiming.COMPLETE)
                event.moneyDeltaOnStart else 0
            record(DayJournalKind.EVENT_START, event.id, startCost)
            val choiceId = after.story.decisions.last().choiceId
            record(if (occurrence.origin == EventOrigin.DEED) DayJournalKind.DEED else DayJournalKind.EVENT_CHOICE,
                choiceId, Math.subtractExact(moneyDelta, startCost), day.energy - before.engine!!.energy)
        }
        else -> Unit
    }
    if (request.command !is EngineCommand.BuyGoalItem) {
        val ownedBefore = before.ownedItems.map { it.id }.toSet()
        after.ownedItems.filter { it.id !in ownedBefore }.forEach { record(DayJournalKind.ITEM_RECEIVED, it.itemId, 0) }
    }
    return after.copy(engine = day.copy(journal = day.journal + entries))
}
