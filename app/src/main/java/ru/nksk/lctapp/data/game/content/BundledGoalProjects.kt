package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.GoalDefinition
import ru.nksk.lctapp.domain.content.GoalRequiredItem
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GoalCampaign

internal const val STARS_GOAL = "figma-stargazing-180-v1"
internal const val TOWER_GOAL = "campaign-tower-kit-v1"
internal const val HOME_GOAL = "campaign-researcher-home-v1"
internal const val MAP_GOAL = "campaign-kingdom-map-v1"
internal const val EXPEDITION_GOAL = "campaign-great-expedition-v1"

/** Temporary parts/prices approved for the first working catalog on 2026-09-20; not Figma price attribution. */
internal fun GameCatalog.withGoalProjects(): GameCatalog {
    data class Part(val id: String, val name: String, val description: String, val price: Long)
    data class Project(val id: String, val title: String, val description: String, val unlock: Int, val scene: String, val parts: List<Part>)
    val projects = listOf(
        Project(TOWER_GOAL, "Подготовка к башне", "Собери личное снаряжение для исследования северной башни. Сам путь откроется по мере истории.", 1, "trail", listOf(
            Part("route", "Карта подходов", "План подходов к старой северной башне.", 30),
            Part("lantern", "Походный фонарь", "Личный источник света для исследования.", 40),
            Part("fastenings", "Крепления и верёвка", "Набор для крепления снаряжения.", 35),
            Part("bridge-kit", "Дорожный набор", "Материалы для подготовки переходов. Покупка сама не ремонтирует мост.", 55),
            Part("transport", "Подготовка перевозки", "Оплаченная подготовка перевозки снаряжения.", 40),
        )),
        Project(HOME_GOAL, "Дом исследователя", "Собери материалы и оборудование для своего дома. Тайны его мастерской раскроются в общей истории.", 2, "workshop", listOf(
            Part("entrance", "Материалы для входа", "Дверь и материалы для входной части дома.", 30),
            Part("roof", "Материалы для крыши", "Покрытие и крепления для крыши.", 50),
            Part("workbench", "Верстак", "Личное рабочее место исследователя.", 45),
            Part("desk", "Рабочий стол", "Стол для записей и будущих находок.", 35),
            Part("shelves", "Полки", "Место для книг, инструментов и коллекции.", 20),
        )),
        Project(MAP_GOAL, "Карта королевства", "Подготовь собственный атлас и инструменты картографа. Новые маршруты появятся после исследований.", 3, "workshop", listOf(
            Part("table", "Картографический стол", "Стол для совмещения слоёв карты.", 60),
            Part("copies", "Архивные материалы", "Материалы для работы с проверенными маршрутами.", 40),
            Part("atlas", "Личный атлас", "Атлас для нанесения открытых дорог.", 60),
            Part("field-kit", "Полевые принадлежности", "Принадлежности для записей в пути.", 40),
        )),
        Project(EXPEDITION_GOAL, "Большая экспедиция", "Последний большой проект: подготовься к путешествию за край известной карты.", 4, "trail", listOf(
            Part("backpack", "Экспедиционный рюкзак", "Личное снаряжение для дальнего пути.", 50),
            Part("compass", "Компас", "Прибор для ориентирования по проверенной карте.", 40),
            Part("light", "Экспедиционный свет", "Освещение для дальней экспедиции.", 40),
            Part("supplies", "Походное оснащение", "Дополнительное оснащение для непредвиденных остановок. Не заменяет ежедневное питание.", 50),
            Part("transport", "Дальняя перевозка", "Подготовленная перевозка в дальний регион.", 70),
        )),
    )
    val items = projects.flatMap { project -> project.parts.map { part ->
        ItemDefinition("${project.id}:${part.id}", part.name, part.description, priceCoins = part.price)
    } }
    return copy(
        content = content.copy(
            goals = content.goals + projects.map { GoalDefinition(it.id, it.title, it.description) },
            items = content.items + items,
            requiredItems = content.requiredItems + projects.flatMap { project ->
                project.parts.map { GoalRequiredItem(project.id, "${project.id}:${it.id}") }
            },
        ),
        goals = goals.map { it.copy(scene = "observatory") } + projects.map { project ->
            GoalCampaign(project.id, storyEventId("G1.01"), project.parts.map { "${project.id}:${it.id}" },
                availableAfterProjects = project.unlock, scene = project.scene,
                requiredCompletedGoalIds = if (project.id == EXPEDITION_GOAL) setOf(STARS_GOAL, TOWER_GOAL, HOME_GOAL, MAP_GOAL) else emptySet())
        },
    )
}
