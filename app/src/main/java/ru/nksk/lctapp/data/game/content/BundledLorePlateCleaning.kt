package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.content.EventChoiceSpec

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
    val spec = EventSpec(
        definition = original.copy(id = LORE_PLATE_CLEANING,
            description = "Смотритель узнаёт знак на пластине, но его скрывает налёт. Можно купить очищающий состав или осторожно почистить пластину самим. Это отнимет немного сил."),
        choices = listOf(
            EventChoiceSpec("pay", "Очистить составом · 3 монеты", moneyDelta = -3, energyCost = 0, facts = facts,
                recap = "Очистили найденную пластину составом и изучили символ"),
            EventChoiceSpec("continue", "Почистить самим · 1 сила", energyCost = CampaignBalance.SMALL_WORK, facts = facts,
                recap = "Сами очистили найденную пластину и изучили символ"),
        ),
        policy = oldPolicy.copy(energyCost = 0, choiceEnergyCosts = emptyMap(), factsByChoiceId = emptyMap(), choiceGameKinds = emptyMap()),
        card = oldCard.copy(
            impact = "", effort = "", summaryByChoiceId = emptyMap(),
            presentation = oldCard.presentation.copy(media = oldCard.presentation.media.copy(game = null)),
        ),
    )
    val compiled = withEventSpecs(listOf(spec))
    return compiled.copy(
        // Keep its choice effects readable; only the current definition belongs to the act.
        policies = compiled.policies + (original.id to oldPolicy.copy(storyActId = null)),
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
