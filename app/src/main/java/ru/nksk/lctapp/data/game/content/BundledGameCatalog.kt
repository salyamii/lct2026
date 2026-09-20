package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** Source texts live in docs/design/content. Version IDs preserve installed definitions. */
internal fun bundledGameCatalog(): GameCatalog {
    val entries = listOf(
        Entry(
            id = "figma-2363-4-v1", type = EventType.STORY,
            title = "Ночь наблюдений",
            body = "Через несколько недель откроется старый небесный зал. Но телескоп пока не готов. Соберём комплект постепенно — это наша большая цель.",
            action = "Начать собирать комплект", reward = 0L, effort = 0,
            card = EventCardCopy("Большая цель", "Цель: 180 монет", "Без траты", "Вернуться позже", "Глава 1 · Обсерватория", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2363-4", "observatory", null),
        ),
        Entry(
            id = "figma-2163-2-v1", type = EventType.EARNING,
            title = "Смотритель просит помочь",
            body = "Перед отплытием нужно пересчитать ящики и подтянуть крепления у лодки. Небольшое дело — и немного монет.",
            action = "Выполнить · +8", reward = 8L, effort = 1,
            card = EventCardCopy("Короткое дело", "+8 монет", "{petName} немного устанет", "Не сейчас", "Короткое дело · Причал", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2163-2", "pier", "caretaker"),
        ),
        Entry(
            id = "figma-2163-43-v1", type = EventType.EARNING,
            title = "Настроить малый телескоп",
            body = "До вечерних наблюдений осталось полчаса. Смотритель просит проверить линзу и шкалу наведения.",
            action = "Настроить · +10", reward = 10L, effort = 1,
            card = EventCardCopy("Короткое дело", "+10 монет", "Немного сил", "Отказаться", "Короткое дело · Обсерватория", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2163-43", "observatory", "caretaker"),
        ),
        Entry(
            id = "figma-2238-120-v1", type = EventType.EARNING,
            title = "Линзы любят чистоту",
            body = "Смотритель просит протереть запасные линзы специальной салфеткой.",
            action = "Помочь +10", reward = 10L, effort = 1,
            card = EventCardCopy("Короткое дело", "+10 монет", "Немного устанет", "Не сейчас", "Короткое дело · Обсерватория", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2238-120", "observatory", "caretaker_lens"),
        ),
        Entry(
            id = "figma-2270-2-v1", type = EventType.EARNING,
            title = "Груз до отплытия",
            body = "Лодка скоро отправляется. Смотритель просит перенести небольшие ящики с причала на борт.",
            action = "Помочь +10", reward = 10L, effort = 3,
            card = EventCardCopy("Короткое дело", "+10 монет", "Сильно устанет", "Не сейчас", "Короткое дело · Порт", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2270-2", "pier", null),
        ),
        Entry(
            id = "figma-2270-54-v1", type = EventType.EARNING,
            title = "Канаты запутались",
            body = "После разгрузки верёвки сбились в тугие узлы. Бобёр просит распутать их и свернуть в аккуратные бухты.",
            action = "Распутать +6", reward = 6L, effort = 2,
            card = EventCardCopy("Короткое дело", "+6 монет", "Средне устанет", "Не сейчас", "Короткое дело · Порт", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2270-54", "pier", null),
        ),
        Entry(
            id = "figma-2270-106-v1", type = EventType.EARNING,
            title = "Посылки по причалам",
            body = "На складе перепутались посылки для разных лодок. Сверь бирки с ведомостью и разложи груз перед отправкой.",
            action = "Разложить +8", reward = 8L, effort = 3,
            card = EventCardCopy("Короткое дело", "+8 монет", "Сильно устанет", "Не сейчас", "Короткое дело · Порт", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2270-106", "pier", null),
        ),
        Entry(
            id = "figma-2270-158-v1", type = EventType.EARNING,
            title = "Успеть до дождя",
            body = "Над портом собираются тучи. Смотритель просит накрыть мешки плотным полотном и закрепить его по краям.",
            action = "Укрыть груз +6", reward = 6L, effort = 1,
            card = EventCardCopy("Короткое дело", "+6 монет", "Немного устанет", "Не сейчас", "Короткое дело · Порт", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2270-158", "pier", null),
        ),
        Entry(
            id = "figma-2270-210-v1", type = EventType.EARNING,
            title = "Знак под слоем пыли",
            body = "На старом ящике почти не видно отметки отправителя. Бобёр просит очистить дощечку и сверить её с записями.",
            action = "Очистить +6", reward = 6L, effort = 1,
            card = EventCardCopy("Короткое дело", "+6 монет", "Немного устанет", "Не сейчас", "Короткое дело · Порт", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2270-210", "pier", null),
        ),
        Entry(
            id = "figma-2289-2-v1", type = EventType.EARNING,
            title = "Заказы на подносе",
            body = "Медведь подготовил колбаски и свёрток с мясом. Просит отнести деревянный поднос к прилавку — покупатель уже ждёт.",
            action = "Отнести +6", reward = 6L, effort = 1,
            card = EventCardCopy("Короткое дело", "+6 монет", "Немного устанет", "Не сейчас", "Короткое дело · Мясная лавка", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2289-2", "fair", null),
        ),
        Entry(
            id = "figma-2289-56-v1", type = EventType.EARNING,
            title = "Крепкий узел",
            body = "Покупки уже завёрнуты в бумагу. Помоги перевязать свёртки бечёвкой, чтобы упаковка не раскрылась по дороге.",
            action = "Перевязать +6", reward = 6L, effort = 1,
            card = EventCardCopy("Короткое дело", "+6 монет", "Немного устанет", "Не сейчас", "Короткое дело · Мясная лавка", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2289-56", "fair", null),
        ),
        Entry(
            id = "figma-2289-110-v1", type = EventType.EARNING,
            title = "Порядок на витрине",
            body = "Медведь показывает, где разместить свежий товар. Протри полки полотенцем и разложи покупки по его подсказкам.",
            action = "Разложить +10", reward = 10L, effort = 3,
            card = EventCardCopy("Короткое дело", "+10 монет", "Сильно устанет", "Не сейчас", "Короткое дело · Мясная лавка", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2289-110", "fair", null),
        ),
        Entry(
            id = "figma-2289-164-v1", type = EventType.EARNING,
            title = "Ни одного забытого заказа",
            body = "В лавке много покупателей. Медведь протягивает планшет с листами и карандаш — помоги записать, кому и что приготовить.",
            action = "Записать +8", reward = 8L, effort = 2,
            card = EventCardCopy("Короткое дело", "+8 монет", "Средне устанет", "Не сейчас", "Короткое дело · Мясная лавка", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2289-164", "fair", null),
        ),
        Entry(
            id = "figma-2289-218-v1", type = EventType.EARNING,
            title = "Весы любят точность",
            body = "Медведь приготовил весы и гирю. Помоги взвесить свёртки: клади каждый на свободную чашу и проверяй равновесие.",
            action = "Взвесить +8", reward = 8L, effort = 2,
            card = EventCardCopy("Короткое дело", "+8 монет", "Средне устанет", "Не сейчас", "Короткое дело · Мясная лавка", "https://www.figma.com/design/bAod1cKtTX9Q8omQ067q3q/?node-id=2289-218", "fair", null),
        ),
    )
    val chapter = "figma-chapter-1-v1"
    val day = "figma-chapter-1-day-v1"
    val goal = "figma-stargazing-v1"
    val deedGames = mapOf(
        "figma-2163-2-v1" to DeedGameKind.COMPARISON,
        "figma-2163-43-v1" to DeedGameKind.PRECISION,
        "figma-2238-120-v1" to DeedGameKind.PRECISION,
        "figma-2270-2-v1" to DeedGameKind.PRECISION,
        "figma-2270-54-v1" to DeedGameKind.MEMORY,
        "figma-2270-106-v1" to DeedGameKind.MEMORY,
        "figma-2270-158-v1" to DeedGameKind.PRECISION,
        "figma-2270-210-v1" to DeedGameKind.MEMORY,
        "figma-2289-2-v1" to DeedGameKind.PRECISION,
        "figma-2289-56-v1" to DeedGameKind.PRECISION,
        "figma-2289-110-v1" to DeedGameKind.MEMORY,
        "figma-2289-164-v1" to DeedGameKind.MEMORY,
        "figma-2289-218-v1" to DeedGameKind.COMPARISON,
    )
    return GameCatalog(
        content = StoryContent(
            chapters = listOf(ChapterDefinition(chapter, "Ночь наблюдений", goal)),
            days = listOf(GameDayDefinition(day, chapter, 1)),
            goals = listOf(GoalDefinition(goal, "Ночь наблюдений", "Собрать комплект для телескопа")),
            events = entries.map { EventDefinition(it.id, it.type, it.title, it.body, null, null, null, 0, null, null) },
            choices = entries.map { EventChoiceDefinition("${it.id}:complete", it.id, 0, it.action, it.reward, null, null, GoalImpact.NEUTRAL) },
        ),
        policies = entries.associate { it.id to EventPolicy(it.effort,
            discardOfferOnDismiss = it.id == "figma-2163-43-v1", deedGameKind = deedGames[it.id]) },
        cards = entries.associate { it.id to it.card },
        rules = EngineRules("ryzhik-2026-09-19-v1", fullEnergy = 5, hungerBlocksAtStep = 3, shortDeedMaxEnergy = 1, weeklyIncome = 100),
        meals = listOf(
            MealDefinition("basic-v1", price = 5, visualStateAfter = null),
            MealDefinition("community-v1", price = 0, visualStateAfter = null, nextMorningEnergy = 3),
        ),
        storyDayId = day, introductionId = entries.first().id,
        deedPool = entries.filter { it.type == EventType.EARNING }.map { it.id },
    ).withEverydayEvents().withFirstGoal().withGoalProjects().withStoryCampaign().withDayRecapCopy()
}

private data class Entry(
    val id: String, val type: EventType, val title: String, val body: String,
    val action: String, val reward: Long, val effort: Int, val card: EventCardCopy,
)
