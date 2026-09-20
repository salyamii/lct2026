package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.GoalDefinition
import ru.nksk.lctapp.domain.content.GoalRequiredItem
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GoalCampaign

/** Figma 2186:524 composition, chapter G1 lore; prices confirmed by the user on 2026-09-19. */
internal fun GameCatalog.withFirstGoal(): GameCatalog {
    val goalId = "figma-stargazing-180-v1"
    val items = listOf(
        ItemDefinition("stargazing-star-map-v1", "Карта звёзд", "Карта созвездий для ночных наблюдений.", priceCoins = 24),
        ItemDefinition("stargazing-tripod-v1", "Штатив", "Устойчивая опора для телескопа.", priceCoins = 36),
        ItemDefinition("stargazing-telescope-v1", "Телескоп", "Телескоп для большого небесного зала.", priceCoins = 90),
        ItemDefinition("stargazing-trip-v1", "Поездка", "Оплаченная поездка на Ночь наблюдений. Для этого приключения.", priceCoins = 30),
    )
    return copy(
        content = content.copy(
            // The old empty placeholder and its chapter remain installed for existing saves.
            goals = content.goals + GoalDefinition(goalId, "Ночь наблюдений",
                "Собери комплект для телескопа и подготовься к открытию старого небесного зала."),
            items = content.items + items,
            requiredItems = content.requiredItems + items.map { GoalRequiredItem(goalId, it.id) },
        ),
        goals = listOf(GoalCampaign(goalId, introductionId, items.map { it.id }, setOf("$introductionId:complete"))),
        policies = policies + (introductionId to policies.getValue(introductionId).copy(goalId = goalId)),
    )
}
