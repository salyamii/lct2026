package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.EventPresentation
import ru.nksk.lctapp.domain.engine.EventCardVariant
import ru.nksk.lctapp.domain.engine.StoryCondition

/**
 * Visible Figma card copy audited 2026-09-29; see campaign-screens-2026-09-29.figma.json.
 * Display overrides leave immutable definitions, recorded outcomes and replay untouched.
 * Authored gameplay adaptations and conditional card variants retain precedence.
 */
internal fun EventPresentation.withCurrentLoreCopy(scene: LoreScene): EventPresentation {
    val current = currentLoreCopy[scene.sourceId]
    return copy(
        title = title ?: current?.title.takeIf { scene.title == null },
        body = body ?: current?.body.takeIf { scene.body == null && scene.variants.isEmpty() },
        bodyVariants = bodyVariants + editedLoreVariants[scene.sourceId].orEmpty(),
    )
}

private data class LoreDisplayCopy(val title: String? = null, val body: String? = null)

/** Editorial corrections requested on 2026-09-29, separate from source transcripts and rules. */
private val editedLoreVariants = mapOf(
    "G1.10" to listOf(EventCardVariant(StoryCondition.Not(fact("clue_light_order")),
        "После настройки телескопа {petName} находит в журнале порядок трёх сигнальных огней. Теперь известен код, которым можно проверить ответ башни.")),
    "G3.01" to listOf(EventCardVariant(StoryCondition.Always,
        "На детали Хроноскопа выбит номер старой мастерской. Смотритель узнаёт его и приглашает заглянуть внутрь. Узнаем, какие приборы здесь чинили.")),
    "N3.WORKBENCH" to listOf(EventCardVariant(StoryCondition.Always,
        "Тико помогает открыть отсек под старым верстаком. Посмотрим, что там спрятано.")),
    "G3.12" to listOf(EventCardVariant(StoryCondition.GoalCollected(HOME_GOAL),
        "Дом обустроен, а загадка старой мастерской раскрыта. Тико остаётся помогать с приборами. Чтобы собрать найденные фрагменты карты, Смотритель предлагает пригласить Луну - сову-картографа.")),
    "G4.02" to listOf(
        EventCardVariant(StoryCondition.OwnsItem("$MAP_GOAL:table"),
            "Луна и {petName} раскладывают фрагменты на новом столе. Сначала совместим дороги, затем отметим сигнальные посты и безопасные пути."),
        EventCardVariant(StoryCondition.Always,
            "Луна раскладывает фрагменты на своём столе. Сначала совместим дороги, затем отметим сигнальные посты и безопасные пути."),
    ),
    "G4.12" to listOf(EventCardVariant(StoryCondition.GoalCollected(MAP_GOAL),
        "Атлас собран. На карте отмечено, в какой стороне искать последний узел Смотрителей. Луна: «Направление есть, но точное место ещё предстоит найти. Готовимся к большой экспедиции».")),
    "G5.01" to listOf(EventCardVariant(StoryCondition.Always,
        "Луна проверяет маршрут к последнему узлу. «Соберём снаряжение для дальней дороги. И оставим запас на еду и неожиданные расходы».")),
    "G5.05" to listOf(EventCardVariant(StoryCondition.Always,
        "У дороги стоит старое убежище от шторма. На стене надпись: «На незнакомом пути береги запас: он поможет переждать непогоду». Заглянем внутрь?")),
)

private val currentLoreCopy = mapOf(
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2363-4
    "G1.01" to LoreDisplayCopy(body = "Смотритель обсерватории, енот: «Скоро откроется старый небесный зал. Телескоп пока не готов. Соберём комплект постепенно - это наша большая цель»."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2371-2
    "G1.02" to LoreDisplayCopy(title = "На ящике странная метка", body = "Портовый смотритель-бобёр: «Это метка грузов для северной башни. Только она давно погасла…»"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2386-2
    "G1.05" to LoreDisplayCopy(title = "Три ответа - в одном свете"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2397-206
    "G1.10" to LoreDisplayCopy(body = "Смотритель настраивает малый телескоп. {petName} читает в журнале: «Северный пост отвечает тремя огнями». Их цвета совпадают с найденным порядком."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2397-257
    "G1.11" to LoreDisplayCopy(body = "Башня повторяет найденный порядок. Смотритель: «Автоматическое реле ещё работает. Значит, старый сигнал сохранился»."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-163
    "G2.04" to LoreDisplayCopy(body = "За мостом сохранился сигнальный пост. Его линзы направлены на башню. Надпись велит проверить автоматический ответ реле перед входом."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-215
    "G2.05" to LoreDisplayCopy(body = "На петле свежая смазка, у реле - металлическая стружка. Кто-то обслуживал башню после закрытия. Журнал дежурного должен объяснить, кто и зачем."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-319
    "G2.07" to LoreDisplayCopy(body = "Журнал: башню закрыли по приказу. Смотритель обсерватории продолжал смазывать реле, чтобы путники могли проверить сигнал перед входом."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2451-475
    "G2.10" to LoreDisplayCopy(body = "Приёмник подтверждает: обслуженное реле связывает башню с обсерваторией. После установки ключа стрелка указывает на её нижний зал - там основной механизм."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2479-56
    "G3.05" to LoreDisplayCopy(title = "Деталь Хроноскопа"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2479-74
    "G3.06" to LoreDisplayCopy(title = "Старая фотография"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-2
    "G3.07" to LoreDisplayCopy(title = "Меняй только одно"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-20
    "G3.08" to LoreDisplayCopy(title = "Станция калибровки"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-38
    "G3.09" to LoreDisplayCopy(title = "Ящик из подвала"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-74
    "G3.11" to LoreDisplayCopy(title = "Тико запускает тестер"),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2480-92
    "G3.12" to LoreDisplayCopy(title = "Дом становится базой", body = "Дом вновь работает как мастерская. Тико остаётся помогать герою. Фрагменты старой карты требуют картографа, и Смотритель обсерватории приглашает знакомую сову Луну."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2558-7
    "G4.01" to LoreDisplayCopy(body = "По приглашению Смотрителя приходит Луна - сова-картограф. Она изучает фрагменты и замечает: «Некоторые маршруты сняли с карты намеренно»."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2558-205
    "G4.12" to LoreDisplayCopy(body = "Карта указывает регион последнего узла и знак восстановления, но не точную станцию. Луна: «Есть направление. Теперь нужна большая экспедиция»."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2574-7
    "G5.01" to LoreDisplayCopy(body = "Перед выходом нужно собрать оборудование и сохранить обязательный резерв на неизвестные расходы."),
    // https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2574-8
    "G5.02" to LoreDisplayCopy(title = "Первый маршрут", body = "Хроноскоп показывает два варианта пути. Как пойдём?"),
)
