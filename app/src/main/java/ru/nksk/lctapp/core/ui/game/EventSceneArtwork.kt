package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R

/** Presentation only: illustrations do not grant objects, introduce NPCs or change the event. */
internal data class EventSceneArtwork(
    @param:DrawableRes val resource: Int,
    val description: String,
    val isCharacter: Boolean = false,
)

private fun npc(@DrawableRes resource: Int, name: String) = EventSceneArtwork(resource, name, true)
private fun item(@DrawableRes resource: Int, name: String) = EventSceneArtwork(resource, name)

/**
 * Compatibility adapter for illustrations of the existing immutable catalog.
 * New event art is authored through EventCardCopy.presentation.media and resolved first by sceneArtwork.
 */
internal fun eventSceneArtwork(eventId: String?, characterKey: String? = null): EventSceneArtwork? {
    val id = eventId?.removeSuffix(":balance-v2")
    return eventIllustrations[id] ?: when (characterKey) {
        "caretaker" -> npc(R.drawable.npc_caretaker_explaining, "Смотритель")
        "caretaker_lens" -> npc(R.drawable.npc_caretaker_cleaning_lens, "Смотритель чистит линзу")
        "carpenter" -> npc(R.drawable.npc_carpenter_explaining, "Бобёр")
        "tiko" -> npc(R.drawable.npc_tiko_body, "Тико")
        "luna" -> npc(R.drawable.npc_luna_body, "Луна")
        else -> null // The actual saved pet remains in the scene when unique artwork is unavailable.
    }
}

@DrawableRes
internal fun eventSceneBackground(eventId: String?, scene: String?): Int = when {
    eventId?.removeSuffix(":balance-v2") in butcherEvents -> R.drawable.location_butcher_shop
    else -> when (scene) {
        "observatory" -> R.drawable.location_observatory_stage
        "pier" -> R.drawable.location_pier_day
        "fair" -> R.drawable.location_fair_day
        "trail" -> R.drawable.location_trail_day
        "workshop" -> R.drawable.location_workshop_day
        "city", "village" -> R.drawable.menu_village
        "gates" -> R.drawable.location_gates
        "windmill" -> R.drawable.location_windmill
        "hill" -> R.drawable.location_hill_day
        else -> when {
            // These cards have no authored location key. Keep the chapter illustration visible
            // instead of the old empty black stage; it is context, not a new interior asset.
            eventId?.startsWith("campaign-choice-v1:G2.") == true -> R.drawable.goal_preview_tower
            eventId?.startsWith("campaign-choice-v1:G4.") == true ||
                eventId?.startsWith("campaign-choice-v1:G5.") == true -> R.drawable.location_trail_day
            else -> R.drawable.menu_village
        }
    }
}

private val butcherEvents = setOf("figma-2289-2-v1", "figma-2289-56-v1", "figma-2289-110-v1",
    "figma-2289-164-v1", "figma-2289-218-v1")

private val eventIllustrations: Map<String, EventSceneArtwork> = buildMap {
    fun add(art: EventSceneArtwork, vararg ids: String) = ids.forEach { put(it, art) }
    fun lore(art: EventSceneArtwork, vararg ids: String) = ids.forEach { put("campaign-choice-v1:$it", art) }
    val caretaker = npc(R.drawable.npc_caretaker_explaining, "Смотритель")
    val carpenter = npc(R.drawable.npc_carpenter_explaining, "Бобёр")
    val tiko = npc(R.drawable.npc_tiko_body, "Тико")
    val luna = npc(R.drawable.npc_luna_body, "Луна")
    val map = item(R.drawable.deed_goods_map, "Карта маршрутов")
    val scroll = item(R.drawable.deed_goods_scroll, "Старинная запись")
    val lens = item(R.drawable.deed_goods_lens, "Линза")
    val plate = item(R.drawable.event_star_plate_1, "Звёздная пластина")
    val tag = item(R.drawable.deed_pair_tag, "Метка маршрута")

    add(caretaker, "figma-2363-4-v1", "figma-2163-2-v1", "figma-2163-43-v1",
        "figma-2270-2-v1", "figma-2270-158-v1")
    add(npc(R.drawable.npc_caretaker_cleaning_lens, "Смотритель чистит линзу"), "figma-2238-120-v1")
    add(carpenter, "figma-2270-54-v1", "figma-2270-210-v1")
    add(tag, "figma-2270-106-v1")
    add(npc(R.drawable.npc_butcher_tray, "Медведь с подносом"), "figma-2289-2-v1")
    add(npc(R.drawable.npc_butcher_tying, "Медведь перевязывает свёрток"), "figma-2289-56-v1")
    add(npc(R.drawable.npc_butcher_pointing, "Медведь показывает на витрину"), "figma-2289-110-v1")
    add(npc(R.drawable.npc_butcher_orders, "Медведь записывает заказы"), "figma-2289-164-v1")
    add(npc(R.drawable.npc_butcher_scales, "Медведь с весами"), "figma-2289-218-v1")

    lore(caretaker, "G1.01", "G1.05", "G1.11", "G1.12", "G2.01", "N2.RETURN", "G3.01")
    lore(carpenter, "G1.02", "G1.04", "G2.02", "G2.03")
    lore(tiko, "G3.04", "N3.WORKBENCH", "G3.05", "G3.11", "G3.12", "G5.08")
    lore(luna, "G4.01", "G4.02", "G4.04", "G4.07", "G4.08", "G4.09", "G4.12",
        "N4.EXTRA_FRAGMENT", "G5.01", "G5.02", "G5.06", "G5.09", "N5.LAST")
    lore(plate, "G1.03")
    lore(tag, "G1.07", "G3.02", "G3.09", "G4.11")
    lore(lens, "G1.08", "G3.08")
    lore(map, "G1.06", "G3.10", "G4.03", "N4.TRAVEL", "G5.03", "N5.HUB", "G5.12")
    lore(scroll, "G2.07", "G2.09", "G3.03", "G3.07", "G5.04", "G5.07", "G5.10")
    lore(item(R.drawable.gear_goal_telescope, "Малый телескоп"), "G1.10")
    lore(item(R.drawable.event_broken_wheel, "Повреждённое колесо"), "N1.WHEEL")
    lore(item(R.drawable.story_chronoscope, "Хроноскоп"), "G2.12")

    // Keep authored NPCs; replace generic objects and fill empty story scenes with
    // the corresponding transparent object from Figma's dedicated source catalog.
    storyObjectIllustrations.forEach { (storyId, art) ->
        val key = "campaign-choice-v1:$storyId"
        if (get(key)?.isCharacter != true) put(key, art)
    }
    put("campaign-choice-v2:G1.03", getValue("campaign-choice-v1:G1.03"))

    add(item(R.drawable.event_broken_wheel, "Повреждённое колесо"), "figma-2164-46-v2")
    add(item(R.drawable.gear_lantern, "Фонарь"), "figma-2164-92-v2")
    add(item(R.drawable.event_backpack_torn, "Рюкзак с порванной лямкой"), "figma-2297-2-v2")
    add(item(R.drawable.event_map_wet, "Промокшая карта"), "figma-2308-2-v2")
    add(item(R.drawable.event_backpack_resin, "Рюкзак в смоле"), "figma-2313-2-v2", "figma-2313-2-v1")
    add(item(R.drawable.event_rope_frayed, "Перетёртая верёвка"), "figma-2320-2-v2")
    add(item(R.drawable.event_tripod_loose, "Шатающийся штатив"), "figma-2320-50-v2")
    add(item(R.drawable.event_lens_mount_bent, "Погнутая оправа"), "figma-2320-98-v2")
    add(item(R.drawable.event_lantern_sooty, "Закопчённый фонарь"), "figma-2320-146-v2")
    add(item(R.drawable.event_workbench_broken, "Просевший верстак"), "figma-2320-194-v2")
    add(item(R.drawable.event_roof_lens_misaligned, "Перекошенная линза"), "figma-2320-242-v2")
    add(item(R.drawable.event_map_plate_scratched, "Поцарапанная пластина"), "figma-2320-290-v2")
    add(item(R.drawable.event_compass_jammed, "Заевший компас"), "figma-2320-338-v2")
    add(item(R.drawable.event_gear_tooth_broken, "Шестерня со сломанным зубцом"), "figma-2320-386-v2")
    add(item(R.drawable.event_key_bent, "Погнутый ключ"), "figma-2326-16-v2")
    add(item(R.drawable.event_scroll_fragile, "Рассыпающийся свиток"), "figma-2326-64-v2")
    add(item(R.drawable.event_journal_unbound, "Журнал с порванным переплётом"), "figma-2326-112-v2")
    add(item(R.drawable.event_star_plate_tarnished, "Звёздная пластина с налётом"), "figma-2326-160-v2", "figma-2326-160-v3")
    add(item(R.drawable.event_lens_cloudy, "Помутневшая линза"), "figma-2326-208-v2")
    add(item(R.drawable.event_signal_lever_jammed, "Заевший сигнальный рычаг"), "figma-2326-256-v2")
    add(item(R.drawable.event_roof_leak, "Повреждённая крыша"), "figma-2326-304-v2")
    add(item(R.drawable.event_map_box_stuck, "Заклинивший ящик с пластинами"), "figma-2326-352-v2")
    add(item(R.drawable.event_calibration_ring_stuck, "Калибровочное кольцо"), "figma-2326-400-v2")
    add(item(R.drawable.event_station_window_broken, "Разбитое окно станции"), "figma-2326-448-v2")
}

private val storyObjectIllustrations: Map<String, EventSceneArtwork>
    get() = buildMap {
        fun add(@DrawableRes res: Int, label: String, vararg ids: String) = ids.forEach { put(it, item(res, label)) }
        add(R.drawable.story_marked_crate, "Ящик со знаком", "G1.02")
        add(R.drawable.story_story_plate, "Пластина со знаком", "G1.03", "G1.08")
        add(R.drawable.story_cargo_journal, "Грузовой журнал", "G1.04")
        add(R.drawable.story_cleaning_worktable, "Стол для очистки пластины", "G1.05")
        add(R.drawable.story_service_emblem, "Старая эмблема службы", "G1.07")
        add(R.drawable.story_stone_post, "Каменный пост", "G1.09")
        add(R.drawable.story_observation_journal, "Журнал наблюдений", "G1.10", "G2.07")
        add(R.drawable.story_north_tower_signal, "Сигнал Северной башни", "G1.11")
        add(R.drawable.story_signal_post, "Сигнальный пост", "G2.04")
        add(R.drawable.story_three_lights_mechanism, "Механизм трёх огней", "G2.06")
        add(R.drawable.story_two_paths_device, "Устройство двух путей", "G2.08")
        add(R.drawable.story_tower_receiver, "Приёмник башни", "G2.10")
        add(R.drawable.story_return_signal, "Обратный сигнал", "G2.11")
        add(R.drawable.story_chronoscope, "Хроноскоп", "G2.12", "G4.07", "G5.04")
        add(R.drawable.story_caretakers_seal, "Печать Смотрителей", "G3.02")
        add(R.drawable.story_letter, "Письмо без отправителя", "G3.03")
        add(R.drawable.story_chronoscope_ring, "Кольцо хроноскопа", "G3.05")
        add(R.drawable.story_old_photograph, "Старая фотография", "G3.06")
        add(R.drawable.story_instruction_journal, "Журнал с инструкцией", "G3.07")
        add(R.drawable.story_signal_lens, "Сигнальная линза", "G3.08")
        add(R.drawable.story_token_crate, "Ящик с жетонами", "G3.09")
        add(R.drawable.story_route_map, "Карта маршрутов", "G3.10")
        add(R.drawable.story_route_tester, "Тестер двух маршрутов", "G3.11")
        add(R.drawable.story_map_table, "Карта на столе", "G3.12", "G4.01")
        add(R.drawable.story_map_missing_region, "Карта с пустой областью", "G4.02", "G4.04", "G4.08", "G5.09")
        add(R.drawable.story_mountain_sign, "Горный указатель", "G4.05")
        add(R.drawable.story_waystone, "Лесной путевой камень", "G4.06")
        add(R.drawable.story_border_beacon, "Пограничный маяк", "G4.10")
        add(R.drawable.story_route_token, "Маршрутный жетон", "G4.11")
        add(R.drawable.story_assembled_map, "Собранная карта", "G4.12", "G5.01", "G5.06", "G5.07")
        add(R.drawable.story_supply_bag, "Припасы в убежище", "G5.05")
        add(R.drawable.story_relay, "Последнее реле", "G5.08")
        add(R.drawable.story_astrolabe, "Астролябия", "G5.10")
        add(R.drawable.story_new_map_book, "Книга новой карты", "G5.12")
    }
