package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.GameCatalog

/** Recap copy for actual choices. Source Figma transcripts and immutable event definitions stay intact. */
internal fun GameCatalog.withDayRecapCopy(): GameCatalog {
    val deeds = mapOf(
        "figma-2163-2-v1" to "Помогли Смотрителю подготовить лодку",
        "figma-2163-43-v1" to "Настроили малый телескоп",
        "figma-2238-120-v1" to "Помогли Смотрителю очистить линзы",
        "figma-2270-2-v1" to "Перенесли ящики с причала на борт",
        "figma-2270-54-v1" to "Помогли бобру распутать канаты",
        "figma-2270-106-v1" to "Разобрали посылки для разных лодок",
        "figma-2270-158-v1" to "Укрыли груз от дождя",
        "figma-2270-210-v1" to "Очистили знак на ящике и сверили отправителя",
        "figma-2289-2-v1" to "Помогли медведю отнести заказ к прилавку",
        "figma-2289-56-v1" to "Перевязали свёртки в лавке",
        "figma-2289-110-v1" to "Навели порядок на витрине",
        "figma-2289-164-v1" to "Помогли медведю записать заказы",
        "figma-2289-218-v1" to "Взвесили свёртки для покупателей",
    )
    val summaries = buildMap {
        deeds.forEach { (event, text) -> put("$event:complete", text) }
        put("figma-2363-4-v1:complete", "Выбрали большую цель — Ночь наблюдений")
        put("figma-2164-2-v1:buy", "Купили кепку исследователя")
        put("figma-2164-2-v1:pass", "Отказались от покупки кепки")
        put("figma-2313-2-v1:pay", "Отдали рюкзак на очистку от смолы")
        put("figma-2313-2-v1:clean", "Сами очистили рюкзак от смолы")
    }
    return copy(cards = cards.mapValues { (eventId, card) ->
        card.copy(summaryByChoiceId = card.summaryByChoiceId + content.choices.filter { it.eventId == eventId }.mapNotNull { choice ->
            val text = summaries[choice.id] ?: card.summaryByChoiceId[choice.id] ?: if (choice.id.endsWith(":skip"))
                "Пропустили дополнительную историю «${content.events.single { it.id == eventId }.title}»" else null
            text?.let { choice.id to it }
        }.toMap())
    })
}
