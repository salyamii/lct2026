package ru.nksk.lctapp.feature.gear.ui

import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.game.OwnedItem

internal data class GearItemUiState(
    val occurrenceId: String,
    val name: String,
    val description: String,
    val priceCoins: Long?,
)

internal data class GearUiState(
    val storyItems: List<GearItemUiState>,
    val accessories: List<GearItemUiState>,
) {
    val itemCount: Int get() = storyItems.size + accessories.size
}

internal sealed interface GearLoadState {
    data object Loading : GearLoadState
    data class Ready(val inventory: GearUiState) : GearLoadState
    data object Error : GearLoadState
}

/** Ownership drives the list. Catalog-only items and the pet's selected look never grant items. */
internal fun gearUiState(owned: List<OwnedItem>, definitions: List<ItemDefinition>): GearUiState {
    val catalog = definitions.associateBy { it.id }
    val story = mutableListOf<GearItemUiState>()
    val accessories = mutableListOf<GearItemUiState>()
    owned.forEach { occurrence ->
        val item = checkNotNull(catalog[occurrence.itemId]) { "Missing owned item: ${occurrence.itemId}" }
        val card = GearItemUiState(occurrence.id, item.name, item.description, item.priceCoins)
        when (item.category) {
            ItemCategory.STORY -> story.add(card)
            ItemCategory.ACCESSORY -> accessories.add(card)
        }
    }
    return GearUiState(story.toList(), accessories.toList())
}
