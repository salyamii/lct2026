package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.EventChoiceSpec
import ru.nksk.lctapp.domain.content.EventDefinition
import ru.nksk.lctapp.domain.content.EventSpec
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.location.GameLocation
import ru.nksk.lctapp.domain.minigame.DeedGameKind

internal const val CHAPTER_BOAT_EVENT = "campaign-choice-v1:N1.TRIP"

/** The ticket is bought once in Goals. This scene performs the already paid journey. */
internal fun GameCatalog.withChapterBoatJourney(): GameCatalog {
    val campaign = checkNotNull(storyCampaign)
    val first = campaign.acts.first()
    val title = "По реке к звёздам"
    val body = "Билет уже оплачен. Проведи лодку до причала, откуда начнётся Ночь наблюдений."
    val compiled = withEventSpecs(listOf(EventSpec(
        definition = EventDefinition(CHAPTER_BOAT_EVENT, EventType.STORY, title, body,
            null, null, null, 0, null, null),
        choices = listOf(EventChoiceSpec("sail", "Отправиться", gameKind = DeedGameKind.BOAT,
            facts = setOf("stargazing_trip_arrived"), energyCost = 0,
            recap = "Доплыли до причала Ночи наблюдений", destination = GameLocation.OBSERVATORY)),
        policy = EventPolicy(0, storyActId = first.id,
            condition = all(StoryCondition.OwnsItem("stargazing-trip-v1"), fact("tower_signal"))),
        card = EventCardCopy("История", "", "", "Вернуться позже", title, "", scene = "pier", character = null,
            presentation = EventPresentation(title = title, body = body,
                media = EventMedia(artworkKey = "story.boat_journey", sceneKey = "scene.pier_day",
                    ambientCueKey = "ambient.port", game = EventGameMedia("Доберись до причала.")))),
    )))
    return compiled.copy(storyCampaign = campaign.copy(acts = campaign.acts.map { act ->
        if (act.id != first.id) act else act.copy(eventIds = act.eventIds.dropLast(1) +
            CHAPTER_BOAT_EVENT + act.finaleId)
    }))
}
