package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.domain.engine.EventMedia
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Resolved illustration data only; event identities and game rules stay outside the UI. */
internal data class StoryGameTheme(val instructions: String, val pairs: List<Int> = emptyList(), val objectRes: Int? = null)

internal fun storyGameTheme(media: EventMedia, kind: DeedGameKind): StoryGameTheme? = media.game?.let { game ->
    StoryGameTheme(storyGameInstructions(kind, game.context), game.pairArtworkKeys.mapNotNull(::eventMediaArtwork),
        eventMediaArtwork(game.objectArtworkKey))
}

/** The board and its control instructions must share the same source of truth. */
internal fun storyGameInstructions(kind: DeedGameKind, context: String? = null): String = listOfNotNull(
    context?.takeIf(String::isNotBlank),
    when (kind) {
        DeedGameKind.MEMORY -> "Открывай по две карточки и находи одинаковые пары."
        DeedGameKind.PRECISION -> "Останови маркер в зелёной зоне."
        DeedGameKind.COMPARISON -> "Сравни значения и выбери большее."
    },
).joinToString(" ")
