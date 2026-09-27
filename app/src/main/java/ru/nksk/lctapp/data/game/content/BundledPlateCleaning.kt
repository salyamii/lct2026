package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.content.EventChoiceSpec
import ru.nksk.lctapp.domain.minigame.DeedGameKind

internal const val LEGACY_PLATE_CLEANING = "figma-2326-160-v2"
internal const val PLATE_CLEANING = "figma-2326-160-v3"

/** Keep installed definitions and past decisions intact; only unresolved cards adopt the new choice. */
internal fun GameCatalog.withPlateCleaningChoice(): GameCatalog {
    val original = content.events.single { it.id == LEGACY_PLATE_CLEANING }
    val paid = content.choices.single { it.eventId == original.id }
    val policy = policies.getValue(original.id)
    val card = cards.getValue(original.id)
    val spec = EventSpec(
        definition = original.copy(id = PLATE_CLEANING,
            description = "На звёздной пластине появился налёт. Можно очистить её специальным составом или аккуратно оттереть самому. Ручная очистка отнимет силы."),
        choices = listOf(
            EventChoiceSpec("pay", "Очистить составом · 3 монеты", paid.moneyDelta,
                recap = "Очистили звёздную пластину составом"),
            EventChoiceSpec("work", "Почистить самому · 2 силы", energyCost = 2, gameKind = DeedGameKind.PRECISION,
                recap = "Почистили звёздную пластину сами"),
        ),
        policy = policy.copy(scheduling = policy.scheduling.copy(previousEventIds = policy.scheduling.previousEventIds + original.id)),
        card = card.copy(impact = "", effort = "", summaryByChoiceId = emptyMap(),
            presentation = card.presentation.copy(media = card.presentation.media.copy(game = StoryGamePresentation.CLEAN_TARNISHED_PLATE.media))),
    )
    return withEventSpecs(listOf(spec)).copy(
        dailyEventPool = dailyEventPool.map { if (it == original.id) PLATE_CLEANING else it },
        eventReplacements = eventReplacements + (original.id to PLATE_CLEANING),
    )
}
