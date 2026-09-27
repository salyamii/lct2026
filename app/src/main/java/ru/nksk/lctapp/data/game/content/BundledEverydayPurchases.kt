package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.pet.PetVisualState

/** Figma purchase situations with live balances; example wallet amounts are not gameplay rules. */
internal fun GameCatalog.withEverydayPurchases(): GameCatalog {
    data class Offer(val node: String, val title: String, val body: String, val price: Long,
        val item: ItemDefinition? = null, val feeds: Boolean = false, val authored: Boolean = true)
    val offers = listOf(
        Offer("2654:2", "Булочка за 6", "Из пекарни пахнет свежей выпечкой. Булочка выглядит аппетитно. Купить её или пройти мимо?", 6, feeds = true),
        Offer("2654:50", "Кепка только сегодня", "{petName} заметил кепку исследователя за 25 монет. Купить её или сохранить деньги на выбранную цель?", 25,
            ItemDefinition("cosmetic-explorer-hat-v2", "Кепка исследователя", "Можно надеть в разделе «Снаряжение».", ItemCategory.ACCESSORY, 25)),
        Offer("2654:98", "Ярмарочная игра за 7", "На ярмарке предлагают бросить кольца за 7 монет. Это развлечение: денежных призов здесь нет. Поиграть или пройти мимо?", 7),
        Offer("2654:146", "Компасный брелок", "{petName} заметил компасный брелок за 9 монет. Он украсит походный образ. Купить его или оставить монеты на другие планы?", 9,
            ItemDefinition("cosmetic-compass-v1", "Компасный брелок", "Украшение путешественника. Можно надеть в разделе «Снаряжение».", ItemCategory.ACCESSORY, 9)),
        Offer("2654:194", "Игрушка за 5", "На прилавке стоит игрушечный кораблик за 5 монет. Купить его для коллекции или сохранить монеты?", 5,
            ItemDefinition("figma-2654-194-toy-boat-v1", "Игрушечный кораблик", "Сувенир с ярмарки для коллекции.", priceCoins = 5)),
        // The artwork exists in Figma; these three offers/prices are temporary game content, not authored Figma event text.
        Offer("56:55", "Очки пилота", "На ярмарке появились очки пилота. Они меняют образ, но не прибавляют сил и заработка.", 18,
            ItemDefinition("cosmetic-pilot-goggles-v1", "Очки пилота", "Можно надеть в разделе «Снаряжение».", ItemCategory.ACCESSORY, 18), authored = false),
        Offer("56:49", "Нашивка путешественника", "Новая нашивка напоминает о дорогах и открытиях. Купить украшение или продолжить копить?", 12,
            ItemDefinition("cosmetic-route-patch-v1", "Нашивка путешественника", "Можно надеть в разделе «Снаряжение».", ItemCategory.ACCESSORY, 12), authored = false),
        Offer("56:64", "Бинокль исследователя", "Красивый бинокль дополнит образ исследователя. Эта покупка не заменяет части большой цели.", 22,
            ItemDefinition("cosmetic-binoculars-v1", "Бинокль исследователя", "Аксессуар для образа. Можно надеть в разделе «Снаряжение».", ItemCategory.ACCESSORY, 22), authored = false),
    )
    val events = offers.map { offer ->
        EventDefinition("figma-${offer.node.replace(':', '-')}-purchase-v2", EventType.WANT,
            offer.title, offer.body, null, null, null, 0, null, null)
    }
    val choices = offers.zip(events).flatMap { (offer, event) -> listOf(
        EventChoiceDefinition("${event.id}:buy", event.id, 0, "Купить · ${offer.price}", -offer.price,
            null, PetVisualState.HAPPY, GoalImpact.NEUTRAL),
        EventChoiceDefinition("${event.id}:pass", event.id, 1, "Пройти мимо", 0, null, null, GoalImpact.NEUTRAL),
    ) }
    val newPolicies = offers.zip(events).associate { (offer, event) ->
        val owned = offer.item?.let { item -> if (item.id == "cosmetic-explorer-hat-v2")
            StoryCondition.OwnsAnyItem(setOf(item.id, "figma-2164-2-explorer-cap-v1"))
            else StoryCondition.OwnsItem(item.id) }
        event.id to EventPolicy(0, condition = owned?.let(StoryCondition::Not) ?: StoryCondition.Always,
            feedsPetChoiceIds = if (offer.feeds) setOf("${event.id}:buy") else emptySet(),
            scheduling = EventSchedulingPolicy(4, if (offer.item != null) "accessories" else "leisure", EverydayEventKind.WANT))
    }
    val copy = offers.zip(events).associate { (offer, event) -> event.id to EventCardCopy(
        "Необязательная покупка", "Цена: ${offer.price} монет", if (offer.feeds) "Заменяет обычный приём пищи сегодня" else "Без расхода сил",
        null, "Ярмарка · Можно отказаться", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=${offer.node.replace(':', '-')}", "fair", null,
        summaryByChoiceId = mapOf("${event.id}:buy" to when { offer.feeds -> "Поели в пекарне"; offer.item != null -> "Купили: ${offer.item.name}"; else -> "Поиграли на ярмарке" },
            "${event.id}:pass" to "Отказались от покупки: ${offer.title}")) }
    return copy(content = content.copy(events = content.events + events, choices = content.choices + choices,
        items = content.items + offers.mapNotNull { it.item }, choiceItemEffects = content.choiceItemEffects + offers.zip(events).mapNotNull { (offer, event) ->
            offer.item?.let { ChoiceItemEffect("${event.id}:purchase-item", "${event.id}:buy", 0, it.id, ItemOperation.ADD) }
        }), policies = policies + newPolicies, cards = cards + copy, dailyEventPool = dailyEventPool + events.map { it.id })
}
