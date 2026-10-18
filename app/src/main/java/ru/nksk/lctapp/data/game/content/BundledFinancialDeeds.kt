package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Independent paid offers; exercise prices are illustrative, never player purchases. */
internal fun GameCatalog.withFinancialDeeds(): GameCatalog {
    val specs = listOf(
        financialDeed("need-want", DeedGameKind.NEED_WANT, "Собраться в поход",
            "Луна собирается в ночной поход. Помоги разобрать вещи: что пригодится в дороге, а что можно взять для радости.",
            "Помогли Луне выбрать нужные для похода вещи", "4007-785", "city", "luna",
            displayBody = "Луна собирается на разные прогулки. Помоги выбрать вещи: что нужно в этот раз, а что можно взять для радости."),
        financialDeed("real-discount", DeedGameKind.REAL_DISCOUNT, "Проверить скидки",
            "В лавке появились яркие ценники. Помоги сравнить сегодняшние цены со вчерашними и найти настоящие скидки.",
            "Сравнили цены и нашли настоящие скидки", "4009-747", "city", null,
            artworkKey = "npc.butcher.orders"),
        financialDeed("coin-balance", DeedGameKind.COIN_BALANCE, "Помочь с покупками",
            "Смотритель составил список покупок, но монет на всё не хватает. Убери необязательные покупки и оставь самое нужное.",
            "Помогли Смотрителю уложить покупки в нужную сумму", "4011-744", "city", "caretaker",
            displayBody = "Смотритель готовится к пикнику, ночной прогулке и ремонту стола. Помоги выбрать покупки для каждого дела так, чтобы хватило монет."),
        financialDeed("financial-trap", DeedGameKind.FINANCIAL_TRAP, "Выбрать выгодное предложение",
            "Бобёр сравнивает несколько предложений. Помоги посчитать, сколько придётся отдать всего, и проверить, успеет ли покупка к нужному дню.",
            "Помогли Бобру выбрать подходящие предложения без лишних трат", "4012-745", "pier", "carpenter"),
        financialDeed("extra-purchase", DeedGameKind.EXTRA_PURCHASE, "Убрать лишнее из списка",
            "Тико готовит вещи для разных дел: починить верстак, сходить в обсерваторию и на рыбалку. Найди в каждом наборе лишний предмет.",
            "Помогли Тико убрать лишние покупки из списков", "4013-749", "workshop", "tiko"),
    )
    return withEventSpecs(specs).copy(deedPool = deedPool + specs.map { it.definition.id })
}

private fun financialDeed(key: String, kind: DeedGameKind, title: String, body: String,
    recap: String, node: String, scene: String, character: String?, artworkKey: String? = null,
    displayBody: String? = null): EventSpec {
    val id = "financial-deed-$key-v1"
    return EventSpec(
        definition = EventDefinition(id, EventType.EARNING, title, body,
            minSatiety = null, maxFatigue = null, petStateOnStart = null,
            moneyDeltaOnStart = 0, budgetSectionOnStart = null, nextChapterId = null),
        choices = listOf(EventChoiceSpec("complete", "Выполнить дело", moneyDelta = 5, recap = recap)),
        policy = EventPolicy(energyCost = 1, deedGameKind = kind,
            scheduling = EventSchedulingPolicy(cooldownDays = 2, family = id)),
        card = EventCardCopy("Короткое дело", "Награда: до 5 монет", "Немного устанет", "Не сейчас",
            "Короткое дело", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=$node",
            scene, character, presentation = EventPresentation(body = displayBody, media = EventMedia(
                artworkKey = artworkKey, sceneKey = when (scene) {
                    "pier" -> "scene.pier_day"
                    "workshop" -> "scene.workshop_day"
                    else -> "scene.village"
                }, characterKey = character,
                game = EventGameMedia(displayBody ?: body)))),
    )
}
