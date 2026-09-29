package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.ItemOperation

/** Waive only authored purchases and food; services and positive rewards keep their actual amounts. */
internal fun EventFactory.choiceMoneyDelta(choiceId: String, demoMode: Boolean): Long {
    val choice = content.choices.first { it.id == choiceId }
    val free = demoMode && (choiceId in policy(choice.eventId).feedsPetChoiceIds ||
        content.choiceItemEffects.any { it.choiceId == choiceId && it.operation == ItemOperation.ADD })
    return if (free && choice.moneyDelta < 0) 0L else choice.moneyDelta
}

/** Shared with the journal so a waived entry cost cannot become fictitious spending and income. */
internal fun EventFactory.eventMoneyDelta(eventId: String, demoMode: Boolean): Long {
    val event = event(eventId)
    val free = demoMode && content.eventItemEffects.any {
        it.eventId == eventId && it.operation == ItemOperation.ADD
    }
    return if (free && event.moneyDeltaOnStart < 0) 0L else event.moneyDeltaOnStart
}
