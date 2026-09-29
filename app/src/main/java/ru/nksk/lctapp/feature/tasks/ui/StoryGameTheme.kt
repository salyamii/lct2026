package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.domain.engine.EventMedia
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Resolved illustration data only; event identities and game rules stay outside the UI. */
internal data class StoryGameTheme(val instructions: String, val objectRes: Int? = null)

internal fun storyGameTheme(media: EventMedia, kind: DeedGameKind): StoryGameTheme? = media.game?.let { game ->
    StoryGameTheme(storyGameInstructions(kind, game.context),
        eventMediaArtwork(game.objectArtworkKey))
}

/** The board and its control instructions must share the same source of truth. */
internal fun storyGameInstructions(kind: DeedGameKind, context: String? = null): String = listOfNotNull(
    context?.takeIf { kind != DeedGameKind.MEMORY && it.isNotBlank() },
    when (kind) {
        DeedGameKind.MEMORY -> "Открывай по две карточки. Найди все пары."
        DeedGameKind.PRECISION -> "Останови маркер в зелёной зоне."
        DeedGameKind.COMPARISON -> "Сравни цены и выбери предмет дороже."
        DeedGameKind.LIGHTS -> "Нажимай на фонари: выбранный фонарь и соседи меняют состояние. Погаси их все."
        DeedGameKind.SEQUENCE -> if (context.isNullOrBlank()) "Повтори вспышки по памяти." else null
        DeedGameKind.PIPES -> "Соедини концы одного цвета линией."
        DeedGameKind.DIFFERENCES -> "Найди отличия между полками."
        DeedGameKind.STACKING -> "Опусти бегущий ящик на предыдущий."
    },
).joinToString(" ")
