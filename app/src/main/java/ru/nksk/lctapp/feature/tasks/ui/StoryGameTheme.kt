package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.domain.engine.EventMedia

/** Resolved illustration data only; event identities and game rules stay outside the UI. */
internal data class StoryGameTheme(val instructions: String, val pairs: List<Int> = emptyList(), val objectRes: Int? = null)

internal fun storyGameTheme(media: EventMedia): StoryGameTheme? = media.game?.let { game ->
    StoryGameTheme(game.instructions, game.pairArtworkKeys.mapNotNull(::eventMediaArtwork),
        eventMediaArtwork(game.objectArtworkKey))
}
