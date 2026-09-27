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

/** Authored activity artwork/instructions; gameplay kind is specified by the event/choice spec. */
internal enum class StoryGamePresentation(val media: EventGameMedia) {
    CLEAN_PLATE(EventGameMedia("Очистим пластину аккуратными движениями. Останавливай маркер в зелёной зоне.")),
    REPAIR_WHEEL(EventGameMedia("Закрепим колесо. Останавливай маркер в зелёной зоне, чтобы подтянуть крепления.")),
    REINFORCE_BRIDGE(EventGameMedia("Укрепим переход вместе с Бобром. Останавливай маркер в зелёной зоне.")),
    SORT_WORKSHOP_FINDS(EventGameMedia("Разберём находки у технического отсека. Найди одинаковые пары приборов.", workshopPairs)),
    OPEN_WORKBENCH(EventGameMedia("Откроем крепления технического отсека. Останавливай маркер в зелёной зоне.")),
    SORT_OLD_SHELF(EventGameMedia("Разложим находки со старой полки. Найди одинаковые пары приборов.", workshopPairs,
        "story.old_photograph")),
    CHECK_ROOF_LENS(EventGameMedia("Проверим крепления под крышей. Останавливай маркер в зелёной зоне.", objectArtworkKey = "story.signal_lens")),
    LIFT_CRATE(EventGameMedia("Аккуратно достанем ящик. Останавливай маркер в зелёной зоне, чтобы удерживать его ровно.")),
    SORT_ARCHIVE(EventGameMedia("Наведём порядок в архиве. Найди одинаковые карты и жетоны.", routePairs)),
    CHECK_TESTER(EventGameMedia("Проверим тестер вместе с Тико. Останавливай маркер в зелёной зоне.", objectArtworkKey = "story.route_tester")),
    SORT_MAP_NOTES(EventGameMedia("Поможем Луне разобрать карты. Найди одинаковые пары карт и записей.", archivePairs, "story.map_missing_region")),
    ASSEMBLE_COMMON_MAP(EventGameMedia("Сверим находки для общей карты. Найди одинаковые пары карт и записей.", archivePairs, "story.map_missing_region")),
    ADJUST_RELAY(EventGameMedia("Настроим реле вместе с Тико. Останавливай маркер в зелёной зоне.", objectArtworkKey = "story.relay")),
    SEW_BACKPACK(EventGameMedia("Зашьём лямку. Останавливай маркер в зелёной зоне, чтобы делать ровные стежки.")),
    RESTORE_WET_MAP(EventGameMedia("Сверим намокшую карту с архивом. Найди одинаковые пары карт и записей.", archivePairs)),
    CLEAN_RESIN(EventGameMedia("Аккуратно очистим рюкзак. Останавливай маркер в зелёной зоне.")),
    TIGHTEN_TRIPOD(EventGameMedia("Подтянем крепления штатива. Останавливай маркер в зелёной зоне.")),
    CLEAN_LANTERN(EventGameMedia("Очистим горелку фонаря. Останавливай маркер в зелёной зоне.")),
    REPAIR_WORKBENCH(EventGameMedia("Укрепим опору верстака. Останавливай маркер в зелёной зоне.")),
    RESTORE_MAP_LINES(EventGameMedia("Восстановим линии карты по записям. Найди одинаковые пары.", archivePairs)),
    UNJAM_COMPASS(EventGameMedia("Очистим механизм компаса. Останавливай маркер в зелёной зоне.")),
    PRESERVE_SCROLL(EventGameMedia("Сохраним записи старого свитка. Найди одинаковые пары записей и карт.", archivePairs)),
    BIND_JOURNAL(EventGameMedia("Сошьём страницы журнала. Останавливай маркер в зелёной зоне, чтобы делать ровные стежки.")),
    CLEAN_TARNISHED_PLATE(EventGameMedia("Аккуратно очистим налёт с пластины. Останавливай маркер в зелёной зоне.",
        objectArtworkKey = "event.star_plate_tarnished")),
    UNJAM_LEVER(EventGameMedia("Разберём и очистим рычаг. Останавливай маркер в зелёной зоне.")),
    FIT_CRATE_LID(EventGameMedia("Подгоним края ящика. Останавливай маркер в зелёной зоне.")),
    REPAIR_WINDOW(EventGameMedia("Закроем разбитое окно досками. Останавливай маркер в зелёной зоне."));
}
