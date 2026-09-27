package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.R

/** Visual context for the concrete work; these props are illustrative, never inventory grants. */
internal data class StoryGameTheme(val instructions: String, val pairs: List<Int> = emptyList(), val objectRes: Int? = null)

private val archivePairs = listOf(R.drawable.story_cargo_journal, R.drawable.story_observation_journal,
    R.drawable.story_letter, R.drawable.story_old_photograph, R.drawable.story_instruction_journal,
    R.drawable.story_route_map, R.drawable.story_map_missing_region, R.drawable.story_assembled_map)
private val routePairs = listOf(R.drawable.story_route_fragment_tower, R.drawable.story_route_fragment_mountain,
    R.drawable.story_route_fragment_winding, R.drawable.story_route_fragment_forest,
    R.drawable.story_bridge_token, R.drawable.story_tower_token, R.drawable.story_route_map, R.drawable.story_route_token)
private val workshopPairs = listOf(R.drawable.deed_goods_lens, R.drawable.deed_pair_key,
    R.drawable.deed_pair_loupe, R.drawable.deed_pair_astrolabe, R.drawable.deed_pair_armillary,
    R.drawable.deed_goods_lantern, R.drawable.event_star_plate_1, R.drawable.story_chronoscope_ring)

internal fun storyGameTheme(eventId: String): StoryGameTheme? = when (eventId.removePrefix("campaign-choice-v1:")) {
    "G1.03" -> StoryGameTheme("Очистим пластину аккуратными движениями. Останавливай маркер в зелёной зоне.")
    "N1.WHEEL" -> StoryGameTheme("Закрепим колесо. Останавливай маркер в зелёной зоне, чтобы подтянуть крепления.")
    "G2.03" -> StoryGameTheme("Укрепим переход вместе с Бобром. Останавливай маркер в зелёной зоне.")
    "N3.ACCESS" -> StoryGameTheme("Разберём находки у технического отсека. Найди одинаковые пары приборов.", workshopPairs)
    "N3.WORKBENCH" -> StoryGameTheme("Откроем крепления технического отсека. Останавливай маркер в зелёной зоне.")
    "G3.06" -> StoryGameTheme("Разложим находки со старой полки. Найди одинаковые пары приборов.", workshopPairs,
        R.drawable.story_old_photograph)
    "G3.08" -> StoryGameTheme("Проверим крепления под крышей. Останавливай маркер в зелёной зоне.", objectRes = R.drawable.story_signal_lens)
    "G3.09" -> StoryGameTheme("Аккуратно достанем ящик. Останавливай маркер в зелёной зоне, чтобы удерживать его ровно.")
    "G3.10" -> StoryGameTheme("Наведём порядок в архиве. Найди одинаковые карты и жетоны.", routePairs)
    "G3.11" -> StoryGameTheme("Проверим тестер вместе с Тико. Останавливай маркер в зелёной зоне.", objectRes = R.drawable.story_route_tester)
    "G4.02" -> StoryGameTheme("Поможем Луне разобрать карты. Найди одинаковые пары карт и записей.", archivePairs, R.drawable.story_map_missing_region)
    "G4.08" -> StoryGameTheme("Сверим находки для общей карты. Найди одинаковые пары карт и записей.", archivePairs, R.drawable.story_map_missing_region)
    "G5.08" -> StoryGameTheme("Настроим реле вместе с Тико. Останавливай маркер в зелёной зоне.", objectRes = R.drawable.story_relay)
    else -> unexpectedWorkTheme(eventId)
}

private fun unexpectedWorkTheme(eventId: String): StoryGameTheme? = when (eventId) {
    "figma-2297-2-v2" -> StoryGameTheme("Зашьём лямку. Останавливай маркер в зелёной зоне, чтобы делать ровные стежки.")
    "figma-2308-2-v2" -> StoryGameTheme("Сверим намокшую карту с архивом. Найди одинаковые пары карт и записей.", archivePairs)
    "figma-2313-2-v1", "figma-2313-2-v2" -> StoryGameTheme("Аккуратно очистим рюкзак. Останавливай маркер в зелёной зоне.")
    "figma-2320-50-v2" -> StoryGameTheme("Подтянем крепления штатива. Останавливай маркер в зелёной зоне.")
    "figma-2320-146-v2" -> StoryGameTheme("Очистим горелку фонаря. Останавливай маркер в зелёной зоне.")
    "figma-2320-194-v2" -> StoryGameTheme("Укрепим опору верстака. Останавливай маркер в зелёной зоне.")
    "figma-2320-290-v2" -> StoryGameTheme("Восстановим линии карты по записям. Найди одинаковые пары.", archivePairs)
    "figma-2320-338-v2" -> StoryGameTheme("Очистим механизм компаса. Останавливай маркер в зелёной зоне.")
    "figma-2326-64-v2" -> StoryGameTheme("Сохраним записи старого свитка. Найди одинаковые пары записей и карт.", archivePairs)
    "figma-2326-112-v2" -> StoryGameTheme("Сошьём страницы журнала. Останавливай маркер в зелёной зоне, чтобы делать ровные стежки.")
    "figma-2326-160-v3" -> StoryGameTheme("Аккуратно очистим налёт с пластины. Останавливай маркер в зелёной зоне.",
        objectRes = R.drawable.event_star_plate_tarnished)
    "figma-2326-256-v2" -> StoryGameTheme("Разберём и очистим рычаг. Останавливай маркер в зелёной зоне.")
    "figma-2326-352-v2" -> StoryGameTheme("Подгоним края ящика. Останавливай маркер в зелёной зоне.")
    "figma-2326-448-v2" -> StoryGameTheme("Закроем разбитое окно досками. Останавливай маркер в зелёной зоне.")
    else -> null
}
