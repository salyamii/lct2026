package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.content.EventChoiceDefinition
import ru.nksk.lctapp.domain.content.EventDefinition

/** Card composition is authored independently of whether an illustration is available. */
enum class EventLayout { SCENE, PURCHASE, INTRODUCTION }

/** Current display copy; stored event/choice identities and effects remain unchanged. */
data class EventPresentation(
    val layout: EventLayout = EventLayout.SCENE,
    val title: String? = null,
    val body: String? = null,
    val locationTitle: String? = null,
    val actionLabels: Map<String, String> = emptyMap(),
    val outcomeLabels: Map<String, String> = emptyMap(),
    val showEffort: Boolean = true,
    val media: EventMedia = EventMedia(),
)

/** Semantic asset/cue keys. Platforms resolve resources; null means no authored media. */
data class EventMedia(
    val artworkKey: String? = null,
    val sceneKey: String? = null,
    val characterKey: String? = null,
    val musicCueKey: String? = null,
    val ambientCueKey: String? = null,
    val narrationCueKey: String? = null,
    val actionAudio: Map<String, EventActionAudio> = emptyMap(),
    val game: EventGameMedia? = null,
)

data class EventActionAudio(val soundCueKey: String? = null, val voiceCueKey: String? = null)

/** Illustrative mini-game content; this does not select a game kind or grant inventory. */
data class EventGameMedia(val instructions: String, val pairArtworkKeys: List<String> = emptyList(),
    val objectArtworkKey: String? = null)

private val defaultEventPresentation = EventPresentation()

fun GameCatalog.presentationFor(eventId: String): EventPresentation =
    cards[eventId]?.presentation ?: defaultEventPresentation

fun GameCatalog.displayTitle(event: EventDefinition): String = presentationFor(event.id).title ?: event.title

fun GameCatalog.displayAction(choice: EventChoiceDefinition): String =
    presentationFor(choice.eventId).actionLabels[choice.id] ?: choice.text

fun GameCatalog.displayOutcome(choice: EventChoiceDefinition): String? =
    presentationFor(choice.eventId).outcomeLabels[choice.id] ?: cards[choice.eventId]?.summaryByChoiceId?.get(choice.id)
