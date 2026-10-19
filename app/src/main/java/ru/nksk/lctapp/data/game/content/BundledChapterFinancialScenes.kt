package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.EventChoiceSpec
import ru.nksk.lctapp.domain.content.EventDefinition
import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ChoiceItemSpec
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.location.GameLocation
import ru.nksk.lctapp.domain.minigame.DeedGameKind

internal const val CHAPTER_SHOP_EVENT = "campaign-choice-v1:N1.SHOP"
internal const val CHAPTER_PACK_EVENT = "campaign-choice-v1:N1.PACK"
internal const val CURRENT_CHAPTER_BOAT_EVENT = "campaign-choice-v2:N1.TRIP"
internal const val ARRIVAL_CHAPTER_FINALE_EVENT = "campaign-choice-v2:G1.12"

/** The Keeper's shared supplies are a local exercise, separate from the child's wallet and goal kit. */
internal fun GameCatalog.withChapterFinancialScenes(): GameCatalog {
    val campaign = checkNotNull(storyCampaign)
    val first = campaign.acts.first()
    val packCondition = all(StoryCondition.OwnsItem("stargazing-trip-v1"), fact("tower_signal"),
        fact("shared_supplies_compared"))
    val boatDefinition = content.events.single { it.id == CHAPTER_BOAT_EVENT }
    val boatPolicy = policies.getValue(CHAPTER_BOAT_EVENT)
    val compiled = withEventSpecs(listOf(
        chapterFinancialScene(
            id = CHAPTER_SHOP_EVENT, act = first.id, title = "Покупки для общей поездки",
            body = "Смотритель выбирает припасы для общей поездки. Помоги сравнить цены на одинаковые вещи.",
            key = "compare", action = "Сравнить покупки", kind = DeedGameKind.SHOPPING_COMPARISON,
            condition = fact("observatory_known"), facts = setOf("shared_supplies_compared"),
            recap = "Сравнили цены на припасы для общей поездки", artwork = "deed.goods_lantern",
        ),
        chapterFinancialScene(
            id = CHAPTER_PACK_EVENT, act = first.id, title = "Сборы в дорогу",
            body = "Смотритель выделил 12 монет на общие припасы. Собери нужное для дороги и оставь 3 монеты про запас.",
            key = "pack", action = "Собрать припасы", kind = DeedGameKind.TRIP_PACKING,
            condition = packCondition, facts = setOf("trip_packed"),
            recap = "Подготовили общие припасы и оставили запас на дорогу", artwork = "story.route_map",
        ),
        // New departures need packing; already offered v1 boards retain their original guard.
        EventSpec(
            definition = boatDefinition.copy(id = CURRENT_CHAPTER_BOAT_EVENT),
            choices = listOf(EventChoiceSpec("sail", "Отправиться", gameKind = DeedGameKind.BOAT,
                facts = setOf("stargazing_trip_arrived"), energyCost = 0,
                recap = "Доплыли до причала Ночи наблюдений", destination = GameLocation.OBSERVATORY)),
            policy = boatPolicy.copy(
                condition = all(boatPolicy.condition, fact("trip_packed")),
                choiceEnergyCosts = emptyMap(), choiceGameKinds = emptyMap(),
                factsByChoiceId = emptyMap(), choiceDestinations = emptyMap(),
                scheduling = boatPolicy.scheduling.copy(family = CHAPTER_BOAT_EVENT,
                    previousEventIds = setOf(CHAPTER_BOAT_EVENT)),
            ),
            card = cards.getValue(CHAPTER_BOAT_EVENT).copy(summaryByChoiceId = emptyMap()),
        ),
        chapterArrivalFinale(first.finaleId),
    ))
    return compiled.copy(
        content = compiled.content.copy(eventItemEffects = compiled.content.eventItemEffects +
            content.eventItemEffects.filter { it.eventId == first.finaleId }.map { effect ->
                effect.copy(id = "$ARRIVAL_CHAPTER_FINALE_EVENT:event-effect:${effect.id}",
                    eventId = ARRIVAL_CHAPTER_FINALE_EVENT)
            }),
        storyCampaign = campaign.copy(
        acts = campaign.acts.map { act ->
            if (act.id != first.id) act else act.copy(finaleId = ARRIVAL_CHAPTER_FINALE_EVENT, eventIds = buildList {
                act.eventIds.forEachIndexed { index, id ->
                    if (id == CHAPTER_BOAT_EVENT) {
                        add(CHAPTER_PACK_EVENT)
                        add(CURRENT_CHAPTER_BOAT_EVENT)
                    } else add(if (id == first.finaleId) ARRIVAL_CHAPTER_FINALE_EVENT else id)
                    if (index == 0) add(CHAPTER_SHOP_EVENT)
                }
            })
        },
        completionAliases = campaign.completionAliases + mapOf(
            CURRENT_CHAPTER_BOAT_EVENT to setOf("$CHAPTER_BOAT_EVENT:sail"),
            CHAPTER_BOAT_EVENT to setOf("$CURRENT_CHAPTER_BOAT_EVENT:sail"),
            // Returning players who already sailed do not have to prepare that trip again.
            CHAPTER_PACK_EVENT to setOf("$CHAPTER_BOAT_EVENT:sail"),
            ARRIVAL_CHAPTER_FINALE_EVENT to (campaign.completionAliases[first.finaleId].orEmpty() +
                content.choices.filter { it.eventId == first.finaleId }.map { it.id }),
            first.finaleId to (campaign.completionAliases[first.finaleId].orEmpty() +
                compiled.content.choices.filter { it.eventId == ARRIVAL_CHAPTER_FINALE_EVENT }.map { it.id }),
        ),
    ))
}

/** Gate only newly offered finales; historical offered cards keep their installed rules. */
private fun GameCatalog.chapterArrivalFinale(oldId: String): EventSpec {
    val event = content.events.single { it.id == oldId }
    val policy = policies.getValue(oldId)
    val card = cards.getValue(oldId)
    fun newChoice(id: String) = "$ARRIVAL_CHAPTER_FINALE_EVENT:${id.removePrefix("$oldId:")}"
    return EventSpec(
        definition = event.copy(id = ARRIVAL_CHAPTER_FINALE_EVENT),
        choices = content.choices.filter { it.eventId == oldId }.sortedBy { it.position }.map { choice ->
            EventChoiceSpec(key = choice.id.removePrefix("$oldId:"), text = choice.text,
                moneyDelta = choice.moneyDelta, budgetSection = choice.budgetSection,
                petStateAfter = choice.petStateAfter, goalImpact = choice.goalImpact,
                energyCost = policy.choiceEnergyCosts[choice.id], gameKind = policy.choiceGameKinds[choice.id],
                facts = policy.factsByChoiceId[choice.id], feedsPet = choice.id in policy.feedsPetChoiceIds,
                disabled = choice.id in policy.disabledChoiceIds, recap = card.summaryByChoiceId[choice.id],
                destination = policy.choiceDestinations[choice.id],
                energyRestore = policy.choiceEnergyRestores[choice.id] ?: 0,
                itemEffects = content.choiceItemEffects.filter { it.choiceId == choice.id }.sortedBy { it.position }.map {
                    ChoiceItemSpec("$ARRIVAL_CHAPTER_FINALE_EVENT:choice-effect:${it.id}", it.itemId, it.operation)
                })
        },
        policy = policy.copy(condition = all(policy.condition, fact("stargazing_trip_arrived")),
            choiceEnergyCosts = emptyMap(), choiceGameKinds = emptyMap(), factsByChoiceId = emptyMap(),
            feedsPetChoiceIds = emptySet(), disabledChoiceIds = emptySet(), choiceDestinations = emptyMap(),
            choiceEnergyRestores = emptyMap(),
            scheduling = policy.scheduling.copy(family = policy.scheduling.family ?: oldId,
                previousEventIds = policy.scheduling.previousEventIds + oldId)),
        card = card.copy(summaryByChoiceId = emptyMap(), presentation = card.presentation.copy(
            actionLabels = card.presentation.actionLabels.mapKeys { newChoice(it.key) },
            outcomeLabels = card.presentation.outcomeLabels.mapKeys { newChoice(it.key) },
            media = card.presentation.media.copy(actionAudio = card.presentation.media.actionAudio.mapKeys { newChoice(it.key) }))),
    )
}

private fun chapterFinancialScene(
    id: String, act: String, title: String, body: String, key: String, action: String,
    kind: DeedGameKind, condition: StoryCondition, facts: Set<String>, recap: String, artwork: String,
) = EventSpec(
    definition = EventDefinition(id, EventType.STORY, title, body, null, null, null, 0, null, null),
    choices = listOf(EventChoiceSpec(key, action, gameKind = kind, facts = facts, energyCost = 0, recap = recap)),
    policy = EventPolicy(0, storyActId = act, condition = condition),
    card = EventCardCopy("История", "", "", "Вернуться позже", title, "", scene = "pier", character = "caretaker",
        presentation = EventPresentation(title = title, body = body,
            media = EventMedia(artworkKey = artwork, sceneKey = "scene.pier_day",
                ambientCueKey = "ambient.port", musicCueKey = StoryChapterPresentation.OBSERVATORY.musicCueKey,
                game = EventGameMedia(body)))),
)
