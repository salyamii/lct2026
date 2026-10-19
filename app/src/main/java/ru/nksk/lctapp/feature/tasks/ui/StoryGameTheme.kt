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
        DeedGameKind.NEED_WANT -> "Прочитай, куда мы собираемся. Эта вещь сейчас нужна или её просто хочется?"
        DeedGameKind.REAL_DISCOUNT -> "Сравни цену вчера и сегодня. Стало дешевле?"
        DeedGameKind.COIN_BALANCE -> "Собери всё для этой задачи и уложись в сумму."
        DeedGameKind.FINANCIAL_TRAP -> "Сравни цены и сроки. Выбери покупку, которая подойдёт и обойдётся дешевле."
        DeedGameKind.EXTRA_PURCHASE -> "Выбери вещь, без которой сейчас можно обойтись."
        DeedGameKind.TRIP_PACKING -> "Собери нужные вещи и оставь монеты про запас."
        DeedGameKind.SHOPPING_COMPARISON -> "Найди тот же товар дешевле."
        DeedGameKind.RECORD_JOURNAL -> "Проследи путь груза по журналу."
        DeedGameKind.RECORD_LENSES -> "Поставь линзы на схему."
        DeedGameKind.RECORD_LIGHTS -> "Перенеси огни из журнала в свою запись."
        DeedGameKind.OBSERVATORY_OPENING -> "Открой купол и подготовь телескоп."
        DeedGameKind.PLATE_HAND, DeedGameKind.PLATE_SOLUTION -> "Сотри налёт с пластины."
        DeedGameKind.LIGHT_INSCRIPTION -> "Освети надпись фонарём."
        DeedGameKind.SERVICE_EMBLEM -> "Переверни эмблему и сравни знаки."
        DeedGameKind.SIGNAL_POST -> "Направь линзы на купол."
        DeedGameKind.CHAPTER_FINALE -> "Собери находки и соедини маршруты."
        DeedGameKind.RING_TOSS -> "Оттяни кольцо и отпусти к колышкам."
    },
).joinToString(" ")
