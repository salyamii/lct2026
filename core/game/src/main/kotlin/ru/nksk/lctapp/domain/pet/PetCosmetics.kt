package ru.nksk.lctapp.domain.pet

import ru.nksk.lctapp.domain.game.GameState

/** Open item IDs are mapped to verified looks; ownership remains in the aggregate inventory. */
data class PetCosmetic(val lookId: String, val title: String, val itemIds: Set<String>)

object PetCosmetics {
    val starterLooks = setOf("PLAIN", "BANDANA", "BACKPACK")
    val starter = listOf(
        PetCosmetic("BANDANA", "Бандана", setOf("starter-bandana-v1")),
        PetCosmetic("BACKPACK", "Рюкзак", setOf("starter-backpack-v1")),
    )
    val purchased = listOf(
        PetCosmetic("HAT", "Шляпа исследователя", setOf("cosmetic-explorer-hat-v2", "figma-2164-2-explorer-cap-v1")),
        PetCosmetic("GLASSES", "Очки пилота", setOf("cosmetic-pilot-goggles-v1")),
        PetCosmetic("ROUTE_PATCH", "Нашивка путешественника", setOf("cosmetic-route-patch-v1")),
        PetCosmetic("COMPASS", "Походный компас", setOf("cosmetic-compass-v1")),
        PetCosmetic("BINOCULARS", "Бинокль исследователя", setOf("cosmetic-binoculars-v1")),
    )
    /** Separate gift identities: the design series and cap color belong to the item, not the fox. */
    val parentRewards = listOf(
        PetCosmetic("CAP_MOSCOW_BLUE", "Кепка с гербом Москвы - синяя", setOf("cosmetic-cap-moscow-blue-v1")),
        PetCosmetic("CAP_MOSCOW_EMERALD", "Кепка с гербом Москвы - изумрудная", setOf("cosmetic-cap-moscow-emerald-v1")),
        PetCosmetic("CAP_MOSCOW_BURGUNDY", "Кепка с гербом Москвы - бордовая", setOf("cosmetic-cap-moscow-burgundy-v1")),
        PetCosmetic("CAP_LCT2026_BLUE", "Кепка ЛЦТ 2026 - синяя", setOf("cosmetic-cap-lct2026-blue-v1")),
        PetCosmetic("CAP_LCT2026_EMERALD", "Кепка ЛЦТ 2026 - изумрудная", setOf("cosmetic-cap-lct2026-emerald-v1")),
        PetCosmetic("CAP_LCT2026_BURGUNDY", "Кепка ЛЦТ 2026 - бордовая", setOf("cosmetic-cap-lct2026-burgundy-v1")),
    )
    private val all = starter + purchased + parentRewards
    fun forItem(itemId: String): PetCosmetic? = all.find { itemId in it.itemIds }
    fun canEquip(state: GameState, lookId: String): Boolean = lookId == "PLAIN" ||
        all.any { it.lookId == lookId && state.ownedItems.any { owned -> owned.itemId in it.itemIds } }
    fun owns(state: GameState, lookId: String): Boolean = canEquip(state, lookId)
}
