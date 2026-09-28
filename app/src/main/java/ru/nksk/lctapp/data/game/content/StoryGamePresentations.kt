package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.engine.EventGameMedia

private val archivePairs = listOf("story.cargo_journal", "story.observation_journal",
    "story.letter", "story.old_photograph", "story.instruction_journal",
    "story.route_map", "story.map_missing_region", "story.assembled_map")
private val routePairs = listOf("story.route_fragment_tower", "story.route_fragment_mountain",
    "story.route_fragment_winding", "story.route_fragment_forest",
    "story.bridge_token", "story.tower_token", "story.route_map", "story.route_token")
private val workshopPairs = listOf("deed.goods_lens", "deed.pair_key",
    "deed.pair_loupe", "deed.pair_astrolabe", "deed.pair_armillary",
    "deed.goods_lantern", "event.star_plate_1", "story.chronoscope_ring")

/** Authored context/artwork; control instructions follow the event/choice's actual game kind. */
internal enum class StoryGamePresentation(val media: EventGameMedia) {
    CLEAN_PLATE(EventGameMedia("Очистим пластину аккуратными движениями.")),
    REPAIR_WHEEL(EventGameMedia("Подтянем крепления колеса.")),
    REINFORCE_BRIDGE(EventGameMedia("Укрепим переход вместе с Бобром.")),
    SORT_WORKSHOP_FINDS(EventGameMedia("Разберём находки у технического отсека.", workshopPairs)),
    OPEN_WORKBENCH(EventGameMedia("Откроем крепления технического отсека.")),
    SORT_OLD_SHELF(EventGameMedia("Разложим находки со старой полки.", workshopPairs,
        "story.old_photograph")),
    CHECK_ROOF_LENS(EventGameMedia("Проверим крепления под крышей.", objectArtworkKey = "story.signal_lens")),
    LIFT_CRATE(EventGameMedia("Аккуратно достанем ящик, удерживая его ровно.")),
    SORT_ARCHIVE(EventGameMedia("Наведём порядок в архиве.", routePairs)),
    CHECK_TESTER(EventGameMedia("Проверим тестер вместе с Тико.", objectArtworkKey = "story.route_tester")),
    SORT_MAP_NOTES(EventGameMedia("Поможем Луне разобрать карты.", archivePairs, "story.map_missing_region")),
    ASSEMBLE_COMMON_MAP(EventGameMedia("Сверим находки для общей карты.", archivePairs, "story.map_missing_region")),
    ADJUST_RELAY(EventGameMedia("Настроим реле вместе с Тико.", objectArtworkKey = "story.relay")),
    SEW_BACKPACK(EventGameMedia("Зашьём лямку ровными стежками.")),
    RESTORE_WET_MAP(EventGameMedia("Сверим намокшую карту с архивом.", archivePairs)),
    CLEAN_RESIN(EventGameMedia("Аккуратно очистим рюкзак.")),
    TIGHTEN_TRIPOD(EventGameMedia("Подтянем крепления штатива.")),
    CLEAN_LANTERN(EventGameMedia("Очистим горелку фонаря.")),
    REPAIR_WORKBENCH(EventGameMedia("Укрепим опору верстака.")),
    RESTORE_MAP_LINES(EventGameMedia("Восстановим линии карты по записям.", archivePairs)),
    UNJAM_COMPASS(EventGameMedia("Очистим механизм компаса.")),
    PRESERVE_SCROLL(EventGameMedia("Сохраним записи старого свитка.", archivePairs)),
    BIND_JOURNAL(EventGameMedia("Разберём выпавшие страницы перед тем, как сшить журнал.", archivePairs)),
    CLEAN_TARNISHED_PLATE(EventGameMedia("Аккуратно очистим налёт с пластины.",
        objectArtworkKey = "event.star_plate_tarnished")),
    UNJAM_LEVER(EventGameMedia("Разберём и очистим рычаг.")),
    FIT_CRATE_LID(EventGameMedia("Разберём находки из ящика, чтобы подогнать его крышку.", archivePairs)),
    REPAIR_WINDOW(EventGameMedia("Закроем разбитое окно досками."));
}
