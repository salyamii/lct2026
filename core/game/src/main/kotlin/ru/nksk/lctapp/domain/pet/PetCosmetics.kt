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
    fun forItem(itemId: String): PetCosmetic? = (starter + purchased).find { itemId in it.itemIds }
    fun canEquip(state: GameState, lookId: String): Boolean = lookId == "PLAIN" ||
        (starter + purchased).any { it.lookId == lookId && state.ownedItems.any { owned -> owned.itemId in it.itemIds } }
    fun owns(state: GameState, lookId: String): Boolean = canEquip(state, lookId)
}
