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
        DeedGameKind.COMPARISON -> "Сравни цены и следуй заданию раунда."
        DeedGameKind.LIGHTS -> "Нажимай на фонари: выбранный фонарь и соседи меняют состояние. Погаси их все."
        DeedGameKind.SEQUENCE -> if (context.isNullOrBlank()) "Повтори вспышки по памяти." else null
        DeedGameKind.PIPES -> "Соедини концы одного цвета линией."
        DeedGameKind.DIFFERENCES -> "Найди отличия между полками."
        DeedGameKind.STACKING -> "Опусти бегущий ящик на предыдущий."
        DeedGameKind.TELESCOPE -> "Собери телескоп и наведи резкость."
        DeedGameKind.MARK -> "Очисти знак и обведи его линии."
        DeedGameKind.WHEEL -> "Соедини части колеса и закрепи их."
        DeedGameKind.STRAP -> "Пришей лямку и закрепи нить."
        DeedGameKind.SIGNAL -> "Передай башне три сигнала."
        DeedGameKind.ROUTE_MAP -> "Соедини отметки на карте."
        DeedGameKind.BOAT -> "Веди лодку пальцем до причала."
        DeedGameKind.NEED_WANT -> "Прочитай условия прогулки и выбери корзину: в дорогу или для радости."
        DeedGameKind.REAL_DISCOUNT -> "Сравни цену сегодня с ценой вчера."
        DeedGameKind.COIN_BALANCE -> "Собери всё для этой задачи и уложись в сумму."
        DeedGameKind.FINANCIAL_TRAP -> "Сравни полную цену и время ожидания."
        DeedGameKind.EXTRA_PURCHASE -> "Выбери предмет, который не нужен для этого дела."
    },
).joinToString(" ")
