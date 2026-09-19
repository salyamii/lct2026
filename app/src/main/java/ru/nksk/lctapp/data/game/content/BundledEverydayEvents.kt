package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.EventCardCopy
import ru.nksk.lctapp.domain.engine.EventPolicy
import ru.nksk.lctapp.domain.engine.GameCatalog

/** Independent Figma cards; chapter-specific failures await their actual item/location guards. */
internal fun GameCatalog.withEverydayEvents(): GameCatalog {
    val cap = "figma-2164-2-v1"
    val resin = "figma-2313-2-v1"
    val item = "figma-2164-2-explorer-cap-v1"
    val events = listOf(
        EventDefinition(cap, EventType.WANT, "Кепка — только сегодня",
            "Кепка исследователя исчезнет с витрины завтра. Купить сейчас или сохранить деньги на путь?",
            null, null, null, 0, null, null),
        EventDefinition(resin, EventType.RANDOM, "Рюкзак испачкан смолой",
            "Рыжик прислонился к свежим доскам на пристани. Смола испачкала рюкзак и склеила лямку. Очистка стоит 4 монеты. Можно почистить самому, но это отнимет силы.",
            null, null, null, 0, null, null),
    )
    val choices = listOf(
        EventChoiceDefinition("$cap:buy", cap, 0, "Купить · 25", -25, null, null, GoalImpact.NEUTRAL),
        EventChoiceDefinition("$cap:pass", cap, 1, "Пройти мимо", 0, null, null, GoalImpact.NEUTRAL),
        // D-040: reserve/budget labels do not create separate purses; spend the shared balance.
        EventChoiceDefinition("$resin:pay", resin, 0, "Оплатить очистку · 4", -4, null, null, GoalImpact.NEUTRAL),
        EventChoiceDefinition("$resin:clean", resin, 1, "Почистить самому", 0, null, null, GoalImpact.NEUTRAL),
    )
    return copy(
        content = content.copy(
            events = content.events + events,
            choices = content.choices + choices,
            items = content.items + ItemDefinition(item, "Кепка исследователя", "Кепка с витрины"),
            choiceItemEffects = content.choiceItemEffects +
                ChoiceItemEffect("$cap:purchase-item", "$cap:buy", 0, item, ItemOperation.ADD),
        ),
        policies = policies + mapOf(
            cap to EventPolicy(0),
            resin to EventPolicy(0, choiceEnergyCosts = mapOf("$resin:clean" to 2)),
        ),
        cards = cards + mapOf(
            cap to EventCardCopy("Редкая находка", "Цена 25 монет", "Исчезнет завтра", null,
                "Импульсная покупка · Только сегодня",
                "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2164-2", "fair", null),
            resin to EventCardCopy("Неожиданная трата", "Очистка 4 монеты", "Самому: средне устанет", "Отложить",
                "Неожиданная трата · Порт",
                "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2313-2", "pier", null),
        ),
        dailyEventPool = dailyEventPool + listOf(cap, resin),
        oneTimeEventIds = oneTimeEventIds + cap,
    )
}
