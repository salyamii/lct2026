package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.GameCatalog

internal const val LEGACY_LORE_PLATE_CLEANING = "campaign-choice-v1:G1.03"
internal const val LORE_PLATE_CLEANING = "campaign-choice-v2:G1.03"

/** Revise this lore choice without changing installed definitions or replaying completed scenes. */
internal fun GameCatalog.withLorePlateCleaningChoice(): GameCatalog {
    val original = content.events.single { it.id == LEGACY_LORE_PLATE_CLEANING }
    val oldChoice = content.choices.single { it.id == "${original.id}:continue" }
    val oldPolicy = policies.getValue(original.id)
    val oldCard = cards.getValue(original.id)
    val campaign = checkNotNull(storyCampaign)
    val facts = oldPolicy.factsByChoiceId.getValue(oldChoice.id)
    val payId = "$LORE_PLATE_CLEANING:pay"
    val manualId = "$LORE_PLATE_CLEANING:continue"
    return copy(
        content = content.copy(
            events = content.events + original.copy(id = LORE_PLATE_CLEANING,
                description = "Смотритель узнаёт знак на пластине, но его скрывает налёт. Можно купить очищающий состав или осторожно почистить пластину самим. Это отнимет немного сил."),
            choices = content.choices + listOf(
                oldChoice.copy(id = payId, eventId = LORE_PLATE_CLEANING, position = 0,
                    text = "Очистить составом · 3 монеты", moneyDelta = -3),
                oldChoice.copy(id = manualId, eventId = LORE_PLATE_CLEANING, position = 1,
                    text = "Почистить самим · 1 сила", moneyDelta = 0),
            ),
        ),
        policies = policies + mapOf(
            // Keep its choice effects readable; only the current definition belongs to the act.
            original.id to oldPolicy.copy(storyActId = null),
            LORE_PLATE_CLEANING to oldPolicy.copy(
                energyCost = 0,
                choiceEnergyCosts = mapOf(payId to 0, manualId to CampaignBalance.SMALL_WORK),
                factsByChoiceId = mapOf(payId to facts, manualId to facts),
                choiceGameKinds = emptyMap(),
            ),
        ),
        cards = cards + (LORE_PLATE_CLEANING to oldCard.copy(impact = "", effort = "",
            summaryByChoiceId = mapOf(
                payId to "Очистили найденную пластину составом и изучили символ",
                manualId to "Сами очистили найденную пластину и изучили символ",
            ))),
        storyCampaign = campaign.copy(
            acts = campaign.acts.map { act -> act.copy(eventIds = act.eventIds.map {
                if (it == original.id) LORE_PLATE_CLEANING else it
            }) },
            completionAliases = campaign.completionAliases + mapOf(
                LORE_PLATE_CLEANING to (campaign.completionAliases[LORE_PLATE_CLEANING].orEmpty() + oldChoice.id),
                original.id to (campaign.completionAliases[original.id].orEmpty() + setOf(payId, manualId)),
            ),
        ),
        eventReplacements = eventReplacements + (original.id to LORE_PLATE_CLEANING),
    )
}
