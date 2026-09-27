package ru.nksk.lctapp.feature.gear.ui

import ru.nksk.lctapp.domain.pet.renderPetText

import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetCosmetics

internal data class GearItemUiState(
    val occurrenceId: String,
    val name: String,
    val description: String,
    val priceCoins: Long?,
    val lookId: String? = null,
    val equipped: Boolean = false,
    val itemId: String = "",
    val artworkRes: Int? = null,
    val pages: List<GearContentPage> = emptyList(),
    val isStarterAccessory: Boolean = false,
)

internal data class GearUiState(
    val storyItems: List<GearItemUiState>,
    val accessories: List<GearItemUiState>,
) {
    val itemCount: Int get() = storyItems.size + accessories.size
    fun ownedOccurrence(occurrenceId: String?): GearItemUiState? =
        occurrenceId?.let { id -> (storyItems + accessories).firstOrNull { it.occurrenceId == id } }
}

internal sealed interface GearLoadState {
    data object Loading : GearLoadState
    data class Ready(val inventory: GearUiState, val busy: Boolean = false, val actionMessage: Int? = null) : GearLoadState
    data object Error : GearLoadState
}

/** Ownership drives the list. Catalog-only items and the pet's selected look never grant items. */
internal fun gearUiState(owned: List<OwnedItem>, definitions: List<ItemDefinition>, petName: String,
    selectedLookId: String = "PLAIN"): GearUiState {
    val catalog = definitions.associateBy { it.id }
    val story = mutableListOf<GearItemUiState>()
    val accessories = mutableListOf<GearItemUiState>()
    owned.forEach { occurrence ->
        val item = checkNotNull(catalog[occurrence.itemId]) { "Missing owned item: ${occurrence.itemId}" }
        // The v1 cap is an immutable STORY definition. Its historical item ID is an explicit
        // cosmetic alias; moving its presentation does not rewrite its purchase or saved catalog.
        val cosmetic = PetCosmetics.forItem(item.id)
        val name = renderPetText(item.name, petName)
        val description = renderPetText(item.description, petName)
        val content = gearItemContent(item.id, name, description)
        val card = GearItemUiState(occurrence.id, name,
            description, item.priceCoins, cosmetic?.lookId,
            cosmetic?.lookId == selectedLookId, item.id, content.artworkRes, content.pages,
            isStarterAccessory = PetCosmetics.starter.any { item.id in it.itemIds })
        when (if (cosmetic != null) ItemCategory.ACCESSORY else item.category) {
            ItemCategory.STORY -> story.add(card)
            ItemCategory.ACCESSORY -> accessories.add(card)
        }
    }
    return GearUiState(story.toList(), accessories.toList())
}
