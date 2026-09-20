package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.EventCardVariant
import ru.nksk.lctapp.domain.engine.StoryCondition

internal fun thirdStoryAct() = listOf(
    LoreScene("G3.01", setOf("researcher_home_open"), fact("chronoscope_unlocked"), "Осмотреть старую мастерскую",
        body = "Смотритель узнаёт на детали Хроноскопа номер старой мастерской. Он разрешает исследовать её технические отсеки. Личный дом и его обстановка остаются отдельным проектом: сейчас нужно понять, как здесь обслуживали приборы.", scene = "workshop"),
    LoreScene("G3.02", setOf("home_watchers_mark", "former_owner_hint"), fact("researcher_home_open"), "Изучить печать", scene = "workshop"),
    LoreScene("G3.03", setOf("chronoscope_ethics_hint"), fact("home_watchers_mark"), "Прочитать письмо", scene = "workshop"),
    LoreScene("N3.ACCESS", setOf("home_repair_started"), fact("home_watchers_mark"), "Подготовить место для осмотра", CampaignBalance.SMALL_WORK,
        "Доступ к приборам", "Нужно убрать завал перед техническим отсеком. После этой небольшой работы можно пригласить механика и исследовать старые приборы.", "workshop"),
    LoreScene("G3.04", setOf("tiko_npc_met", "mechanic_help_available"), fact("home_repair_started"), "Познакомиться с Тико",
        body = "Тико слышал об исследовании старой мастерской и приходит помочь. «Сначала поймём, как эти приборы работали, а потом будем что-то менять». {petName} показывает ему печать Смотрителей.", scene = "workshop"),
    LoreScene("N3.WORKBENCH", setOf("workbench_access_ready"), fact("tiko_npc_met"), "Открыть технический отсек", CampaignBalance.SMALL_WORK,
        "Под старым верстаком", "Тико помогает безопасно открыть нижний отсек старого верстака. Покупать личный верстак для этого исследования не требуется.", "workshop"),
    LoreScene("G3.05", setOf("chronoscope_service_part", "house_service_station"), all(fact("tiko_npc_met"), fact("workbench_access_ready")), "Исследовать найденное кольцо", scene = "workshop"),
    LoreScene("G3.06", setOf("service_station_visual_confirm"), fact("house_service_station"), "Разобрать старую полку", CampaignBalance.SMALL_WORK, scene = "workshop"),
    LoreScene("G3.07", setOf("chronoscope_single_change_rule"), fact("chronoscope_service_part"), "Открыть ящик с инструкцией", scene = "workshop"),
    LoreScene("G3.08", setOf("calibration_station_known", "small_relay_restorable"), fact("house_service_station"), "Осмотреть крепление под крышей", CampaignBalance.SMALL_WORK, scene = "workshop"),
    LoreScene("G3.09", setOf("route_tokens_found"), fact("calibration_station_known"), "Достать ящик из подвала", CampaignBalance.SMALL_WORK, scene = "workshop"),
    LoreScene("G3.10", setOf("old_route_fragments", "kingdom_map_hook"), fact("calibration_station_known"), "Разобрать технический архив", CampaignBalance.SMALL_WORK, scene = "workshop"),
    LoreScene("G3.11", setOf("chronoscope_tester_checked"), all(fact("chronoscope_single_change_rule"), fact("mechanic_help_available"), fact("workbench_access_ready")), "Проверить тестер", scene = "workshop"),
    LoreScene("G3.12", setOf("research_station_ready", "luna_teaser"), fact("old_route_fragments"), "Завершить исследование станции",
        title = "Мастерская раскрывает своё прошлое",
        body = "Техническая часть мастерской снова доступна исследователям. Тико помог разобраться с Хроноскопом, а личный комплект {petName} собран. Среди документов находятся фрагменты карты королевства — теперь нужен картограф. Уютный собственный дом остаётся отдельным проектом.", scene = "workshop",
        variants = listOf(EventCardVariant(StoryCondition.GoalCollected(HOME_GOAL), "Личный дом обустроен, а его техническое прошлое наконец понятно. Тико остаётся помогать в мастерской. Найденные фрагменты карты слишком сложны без картографа — Смотритель предлагает пригласить Луну."))),
)

private val mapFragments = setOf("coast_fragment", "archive_fragment", "mountain_fragment", "forest_fragment", "extra_archive_fragment")
private fun fragments(count: Int) = StoryCondition.FactsAtLeast(mapFragments, count)

internal fun fourthStoryAct() = listOf(
    LoreScene("G4.01", setOf("luna_npc_met", "map_reconstruction_started"), fact("old_route_fragments"), "Показать фрагменты Луне", scene = "workshop"),
    LoreScene("G4.02", setOf("map_layers_known", "luna_trust"), fact("luna_npc_met"), "Разложить фрагменты на столе Луны",
        body = "Луна предоставляет свой картографический стол. Она объясняет: старые карты собираются слоями — дороги, сигнальные посты и безопасные варианты пути. Личный атлас можно собирать отдельно.", scene = "workshop",
        variants = listOf(EventCardVariant(StoryCondition.OwnsItem("$MAP_GOAL:table"), "На личном картографическом столе {petName} Луна раскладывает найденные фрагменты по слоям: дороги, сигнальные посты и безопасные варианты маршрутов."))),
    LoreScene("G4.03", setOf("coast_fragment", "coast_route_options"), fact("map_layers_known"), "Сходить за береговыми картами", CampaignBalance.TRAVEL, scene = "pier"),
    LoreScene("G4.04", setOf("removed_route_confirmed", "archive_fragment"), all(fact("map_layers_known"), fact("keeper_trust")), "Изучить архивную копию", scene = "workshop"),
    LoreScene("G4.05", setOf("route_choice_design_known", "mountain_fragment"), fact("removed_route_confirmed"), "Сходить к горному указателю", CampaignBalance.TRAVEL, scene = "trail"),
    LoreScene("G4.06", setOf("forest_fragment", "watchers_route_philosophy"), fact("route_choice_design_known"), "Исследовать лесной путь", CampaignBalance.TRAVEL, scene = "trail"),
    LoreScene("G4.07", setOf("route_counterfactual_unlocked", "chronoscope_not_forecast_confirmed"), all(fact("chronoscope_unlocked"), fact("route_choice_design_known"), fragments(2)), "Сравнить записи о маршрутах"),
    LoreScene("N4.EXTRA_FRAGMENT", setOf("extra_archive_fragment"), all(fact("route_counterfactual_unlocked"), StoryCondition.Not(fragments(3))), "Сверить запасную архивную копию",
        title = "Недостающий участок", body = "Береговой и лесной фрагменты ещё не найдены. Смотритель приносит запасную архивную копию. Луна сверяет её с двумя известными участками: теперь хватает проверенных фрагментов, чтобы продолжить исследование.", scene = "workshop", requiredInOrder = false),
    LoreScene("G4.08", setOf("blank_region_known", "erased_region_mystery"), all(fragments(3), fact("archive_fragment")), "Совместить проверенные фрагменты", scene = "workshop"),
    LoreScene("G4.09", setOf("luna_personal_hook", "border_beacon_hint"), all(fact("luna_trust"), fact("blank_region_known")), "Выслушать Луну", scene = "workshop"),
    LoreScene("N4.TRAVEL", setOf("border_route_reached"), fact("blank_region_known"), "Дойти до границы карты", CampaignBalance.TRAVEL,
        "Край известных дорог", "Проверенная схема позволяет добраться до границы карты и осмотреть сохранившиеся сигнальные посты.", "trail"),
    LoreScene("G4.10", setOf("border_beacon_found", "unknown_region_direction"), all(fact("blank_region_known"), fact("border_route_reached")), "Осмотреть пограничный маяк", scene = ""),
    LoreScene("G4.11", setOf("final_node_hint"), all(fact("route_tokens_found"), fact("border_beacon_found")), "Сверить маршрутный жетон", scene = ""),
    LoreScene("G4.12", setOf("research_map_complete", "final_node_location_known"), fact("border_beacon_found"), "Закончить общую схему",
        title = "Направление за краем карты",
        body = "Общая исследовательская схема готова: маяк подтверждает направление последнего крупного узла Смотрителей. Личный комплект {petName} тоже собран. Луна предупреждает: «Теперь путь известен, но впереди большая экспедиция». Собственный атлас и общие открытия учитываются отдельно.", scene = "workshop",
        variants = listOf(EventCardVariant(StoryCondition.GoalCollected(MAP_GOAL), "Личный атлас собран, и новые открытия нанесены на исследовательскую схему. За границей известных дорог отмечен последний крупный узел Смотрителей. Луна: «Теперь у нас есть направление. Придётся готовить большую экспедицию»."))),
)

internal fun fifthStoryAct() = listOf(
    LoreScene("G5.01", setOf("final_region_route_active", "expedition_started"), fact("final_node_location_known"), "Обсудить дальний маршрут",
        body = "Луна проверяет направление последнего узла. «Снаряжение собираем постепенно. Учти ежедневную еду и неизвестные расходы в дороге». Деньги остаются общими: совет о запасе не блокирует покупки.", scene = "trail"),
    LoreScene("G5.02", condition = fact("expedition_started"), scene = "trail",
        body = "К релейной станции ведут два проверенных пути. За быстрый переход нужно заплатить, а обход потребует сил. Луна помогает сравнить расходы, решение остаётся за тобой.",
        options = listOf(
            LoreOption("fast", "Быстрый путь · ${CampaignBalance.FAST_ROUTE_COINS} монет", setOf("fast_route_taken", "reached_relay_station"), moneyDelta = -CampaignBalance.FAST_ROUTE_COINS),
            LoreOption("detour", "Обход · немного устанет", setOf("safe_route_taken", "reached_relay_station"), CampaignBalance.DETOUR),
            LoreOption("skip", "Оставить обычный маршрут", emptySet()),
        )),
    LoreScene("N5.TRAVEL", setOf("reached_relay_station"), all(fact("expedition_started"), StoryCondition.Not(fact("reached_relay_station"))), "Дойти до релейной станции", CampaignBalance.TRAVEL,
        "Дорога к релейной станции", "Группа продолжает путь по проверенному обычному маршруту. Переход потребует немного сил.", "trail", requiredInOrder = false),
    LoreScene("G5.03", setOf("relay_station_found", "final_network_hub_hint"), fact("reached_relay_station"), "Изучить станционную карту", scene = ""),
    LoreScene("G5.04", setOf("chronoscope_truth_known", "campaign_theme_explicit"), all(fact("relay_station_found"), fact("chronoscope_unlocked")), "Прочитать журнал станции", scene = ""),
    LoreScene("G5.05", setOf("unknown_route_reserve_hint"), fact("chronoscope_truth_known"), "Заглянуть в старое штормовое убежище", CampaignBalance.TRAVEL,
        body = "Рядом сохранилось убежище на случай шторма. На стене Смотрители оставили правило: «Если путь неизвестен, резерв — часть маршрута, а не лишний груз». Можно осмотреть его по дороге; сейчас шторм не начинается.", scene = ""),
    LoreScene("N5.HUB", setOf("reached_network_hub"), fact("final_network_hub_hint"), "Дойти до центрального узла", CampaignBalance.TRAVEL,
        "Сходящиеся маршруты", "Карта релейной станции показывает дорогу к центральному узлу. Группа проверяет ориентиры и продолжает путь.", "trail"),
    LoreScene("G5.06", setOf("multi_route_network_confirmed", "final_station_coordinates_partial"), fact("reached_network_hub"), "Изучить варианты маршрутов", scene = ""),
    LoreScene("G5.07", setOf("network_shutdown_reason_known", "outdated_route_data_known"), fact("multi_route_network_confirmed"), "Проверить архив после шторма", scene = ""),
    LoreScene("G5.08", setOf("final_relay_restored"), all(fact("tiko_npc_met"), StoryCondition.GoalCollected(HOME_GOAL), fact("reached_network_hub"), StoryCondition.OwnsItem("$EXPEDITION_GOAL:supplies")), "Помочь Тико восстановить реле", CampaignBalance.SMALL_WORK,
        body = "Среди походного оснащения есть инструменты для старого реле. Тико помогает восстановить его: «Механизм старый, но идея нормальная. Главное — не считать старые данные вечной правдой».", scene = ""),
    LoreScene("G5.09", setOf("final_station_location_known", "final_leg_unlocked"), all(fact("final_station_coordinates_partial"), fact("research_map_complete"), fact("outdated_route_data_known")), "Сверить карту с новыми данными", scene = ""),
    LoreScene("N5.LAST", setOf("final_station_entered"), fact("final_station_location_known"), "Дойти до последней станции", CampaignBalance.TRAVEL,
        "Последний отрезок", "Луна уточнила безопасные ориентиры. Осталось пройти к станции за долиной и найти запись последнего Смотрителя.", "trail"),
    LoreScene("G5.10", setOf("last_keeper_message", "second_path_meaning_known"), fact("final_station_entered"), "Прочитать последнее послание", scene = ""),
    LoreScene("G5.11", setOf("beacon_chain_restored"), all(fact("last_keeper_message"), fact("final_relay_restored")), "Проверить ответ маяков", scene = ""),
    LoreScene("G5.12", setOf("campaign_1_complete"), fact("last_keeper_message"), "Завершить историю", scene = "",
        variants = listOf(EventCardVariant(StoryCondition.Not(fact("beacon_chain_restored")), "Герои восстановили знания Смотрителей и нашли последнюю станцию. Часть маяков пока молчит: ремонт сети ещё впереди. Хроноскоп помогает сравнивать решения, но не выбирает за путешественника. {petName} отмечает первую точку за старой картой. Дальше путь предстоит строить самим."))),
)
