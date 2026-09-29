package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.EventLayout
import ru.nksk.lctapp.domain.engine.EventMedia
import ru.nksk.lctapp.domain.engine.EventPresentation

/** Soundtrack is authored with lore groups; the app uses the current story act across screens. */
internal enum class StoryChapterPresentation(val musicCueKey: String) {
    OBSERVATORY("story.chapter_1"),
    TOWER("story.chapter_2"),
    WORKSHOP("story.chapter_3"),
    MAP("story.chapter_4"),
    EXPEDITION("story.chapter_5"),
}

/** Current display metadata is authored beside a typed offer, not selected by UI event IDs. */
internal enum class PurchasePresentation(private val template: EventPresentation) {
    BUN(purchase("Ароматная булочка",
            "Заменяет обычный приём пищи. {petName} будет доволен: она гораздо вкуснее обычного обеда.",
            "purchase.bakery_bun", location = "Пекарня", showEffort = false)),
    LEGACY_EXPLORER_HAT(purchase("Кепка исследователя",
            "{petName} примеряет кепку путешественника. Её можно надеть и носить в новых приключениях.",
            "accessory.explorer_hat")),
    EXPLORER_HAT(purchase("Кепка исследователя",
            "{petName} примеряет кепку путешественника. Её можно надеть и носить в новых приключениях.",
            "purchase.explorer_hat")),
    RING_TOSS(purchase("Кольцеброс",
            "Бросим кольца и проверим меткость? Это весёлое развлечение без денежных призов.",
            "purchase.ring_toss", action = "Сыграть")),
    COMPASS(purchase("Компасный брелок",
            "Маленький компас украсит походный образ. Его можно надеть в снаряжении.",
            "purchase.compass_keychain")),
    TOY_BOAT(purchase("Игрушечный кораблик",
            "Кораблик с маленьким парусом пополнит нашу коллекцию. Он останется в снаряжении.",
            "purchase.toy_boat")),
    PILOT_GOGGLES(purchase("Очки пилота",
            "{petName} примеряет очки пилота. С ними можно отправиться на прогулку в новом образе.",
            "accessory.pilot_goggles")),
    ROUTE_PATCH(purchase("Нашивка путешественника",
            "Нашивка с дорожным знаком украсит походный образ.", "accessory.route_patch")),
    BINOCULARS(purchase("Бинокль исследователя",
            "Бинокль дополнит образ исследователя. Его можно надеть в снаряжении.", "accessory.binoculars"));

    fun forEvent(eventId: String): EventPresentation = template.copy(
        actionLabels = template.actionLabels.mapKeys { (suffix, _) -> "$eventId:$suffix" },
        outcomeLabels = mapOf("$eventId:pass" to "Отказались от покупки: ${template.title}"),
        media = template.media.copy(actionAudio = paymentActionAudio(eventId, listOf("buy"))))
}

private fun purchase(title: String, body: String, artwork: String, action: String = "Купить",
    location: String = "Ярмарка", showEffort: Boolean = true) = EventPresentation(
    layout = EventLayout.PURCHASE, title = title, body = body, locationTitle = location,
    actionLabels = mapOf("buy" to action, "pass" to "Пройти мимо"), showEffort = showEffort,
    media = EventMedia(artworkKey = artwork, appearanceCueKey = "sound.purchase_appears"),
)

/** Current story copy is selected in its LoreScene without rewriting historical definitions. */
internal enum class StoryPresentation(private val template: EventPresentation, private val actionLabel: String? = null) {
    LUNA_NOTE(EventPresentation(title = "Старая заметка Луны")),
    OBSERVATORY_INVITATION(EventPresentation(layout = EventLayout.INTRODUCTION,
            body = "Смотритель зовёт нас на Ночь наблюдений.\n\nПоможем подготовить телескоп и разгадаем старые загадки.",
            locationTitle = "История", showEffort = false,
            media = EventMedia(sceneKey = "goal.stargazing")), actionLabel = "В обсерваторию"),
    HALL_OF_PATHS(EventPresentation(title = "Зал других путей",
            body = "Свиток помог найти вход, а башенный ключ открывает дверь в нижний зал. Здесь Смотрители хранили записи о разных путях. Узнаем, что они исследовали.",
        ), actionLabel = "Исследовать зал"),
    WORKBENCH_PART(EventPresentation(title = "Деталь под верстаком",
        body = "Под верстаком лежит кольцо от прибора Смотрителей. На нём - метки для настройки и номер этого дома. Значит, здесь чинили приборы, которые сравнивают разные пути.")),
    OLD_ROUTES(EventPresentation(title = "Два старых маршрута",
        body = "Луна находит записи двух маршрутов. {petName} помогает увидеть различия: время в пути, нужный запас и места для остановок. У каждого пути свои возможности.")),
    FAST_OR_RELIABLE(EventPresentation(title = "Как добраться до станции",
        body = "До станции можно добраться коротким платным путём или бесплатно по обходу. На обходе {petName} немного устанет. Как пойдём?")),
    POSSIBILITY_NOT_PROMISE(EventPresentation(title = "Возможность, а не обещание",
        body = "В журнале Смотрителей есть важная заметка: другие пути помогают понять последствия выбора. Они не обещают, что будущее обязательно сложится именно так.")),
    WATCHERS_POWER(EventPresentation(title = "Другие пути ещё ждут",
        body = "Мы дошли до последней станции и открыли часть тайн Смотрителей. Но на карте остались неизведанные места, а за каждым решением - пути, которыми мы ещё не ходили.\n\nСила Смотрителей поможет вернуться к началу. Можно снова выбрать героя, принимать другие решения и узнать, куда они приведут. История этого приключения сохранится - к ней всегда можно вернуться."),
        actionLabel = "Воспользоваться силой");

    fun forEvent(eventId: String, choiceKeys: List<String>): EventPresentation = template.copy(
        actionLabels = actionLabel?.let { label -> choiceKeys.associate { "$eventId:$it" to label } }
            ?: template.actionLabels,
    )
}
