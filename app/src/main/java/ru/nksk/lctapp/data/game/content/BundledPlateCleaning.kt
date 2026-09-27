package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.GameCatalog

internal const val LEGACY_PLATE_CLEANING = "figma-2326-160-v2"
internal const val PLATE_CLEANING = "figma-2326-160-v3"

/** Keep installed definitions and past decisions intact; only unresolved cards adopt the new choice. */
internal fun GameCatalog.withPlateCleaningChoice(): GameCatalog {
    val original = content.events.single { it.id == LEGACY_PLATE_CLEANING }
    val paid = content.choices.single { it.eventId == original.id }
    val policy = policies.getValue(original.id)
    val card = cards.getValue(original.id)
    return copy(
        content = content.copy(
            events = content.events + original.copy(id = PLATE_CLEANING,
                description = "На звёздной пластине появился налёт. Можно очистить её специальным составом или аккуратно оттереть самому. Ручная очистка отнимет силы."),
            choices = content.choices + listOf(
                paid.copy(id = "$PLATE_CLEANING:pay", eventId = PLATE_CLEANING,
                    text = "Очистить составом · 3 монеты"),
                paid.copy(id = "$PLATE_CLEANING:work", eventId = PLATE_CLEANING,
                    position = 1, text = "Почистить самому · 2 силы", moneyDelta = 0),
            ),
        ),
        policies = policies + (PLATE_CLEANING to policy.copy(
            choiceEnergyCosts = mapOf("$PLATE_CLEANING:work" to 2),
            scheduling = policy.scheduling.copy(previousEventIds = policy.scheduling.previousEventIds + original.id),
        )),
        cards = cards + (PLATE_CLEANING to card.copy(impact = "", effort = "", summaryByChoiceId = mapOf(
            "$PLATE_CLEANING:pay" to "Очистили звёздную пластину составом",
            "$PLATE_CLEANING:work" to "Почистили звёздную пластину сами",
        ))),
        dailyEventPool = dailyEventPool.map { if (it == original.id) PLATE_CLEANING else it },
        eventReplacements = eventReplacements + (original.id to PLATE_CLEANING),
    )
}
