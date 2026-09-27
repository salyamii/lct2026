package ru.nksk.lctapp.domain.pet

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem

/** The single accessory chosen before the adventure is owned, just like later equipment. */
fun GameState.withStarterAccessoryOwnership(startingLookId: String = pet.selectedLookId): GameState {
    val accessory = PetCosmetics.starter.firstOrNull { it.lookId == startingLookId } ?: return this
    if (ownedItems.any { it.itemId in accessory.itemIds }) return this
    val baseId = "starter-accessory:${accessory.lookId}"
    val occurrenceId = generateSequence(baseId) { previous -> "$previous:1" }
        .first { candidate -> ownedItems.none { it.id == candidate } }
    return copy(ownedItems = ownedItems + OwnedItem(occurrenceId, accessory.itemIds.single()))
}
