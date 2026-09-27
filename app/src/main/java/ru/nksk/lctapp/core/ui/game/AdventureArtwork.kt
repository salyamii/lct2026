package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.pet.PetState

/** The menu and narrative scenes use the same saved appearance, including missing-art states. */
@DrawableRes
internal fun adventurePetArtwork(pet: PetState): Int? = pet.toAdventurePetPresentation().artworkRes

@DrawableRes
internal fun adventureLocationArtwork(locationId: String): Int = when (locationId.lowercase()) {
    "observatory" -> R.drawable.location_observatory_stage
    "gates" -> R.drawable.location_gates
    "windmill" -> R.drawable.location_windmill
    "workshop" -> R.drawable.location_workshop_day
    "hill" -> R.drawable.location_hill_day
    "trail" -> R.drawable.location_trail_day
    "fair" -> R.drawable.location_fair_day
    "pier" -> R.drawable.location_pier_day
    else -> R.drawable.location_city
}

/** The same collection illustration identifies a goal part before and after its purchase. */
@DrawableRes
internal fun goalItemArtwork(itemId: String): Int? = when (itemId) {
    "stargazing-star-map-v1" -> R.drawable.collection_stars_map
    "stargazing-tripod-v1" -> R.drawable.collection_stars_tripod
    "stargazing-telescope-v1" -> R.drawable.collection_stars_telescope
    "stargazing-trip-v1" -> R.drawable.collection_stars_ticket
    "campaign-tower-kit-v1:route" -> R.drawable.collection_tower_route
    "campaign-tower-kit-v1:lantern" -> R.drawable.collection_tower_lantern
    "campaign-tower-kit-v1:fastenings" -> R.drawable.collection_tower_fastenings
    "campaign-tower-kit-v1:bridge-kit" -> R.drawable.collection_tower_bridge_kit
    "campaign-tower-kit-v1:transport" -> R.drawable.collection_tower_transport
    "campaign-researcher-home-v1:entrance" -> R.drawable.collection_home_entrance
    "campaign-researcher-home-v1:roof" -> R.drawable.collection_home_roof
    "campaign-researcher-home-v1:workbench" -> R.drawable.collection_home_workbench
    "campaign-researcher-home-v1:desk" -> R.drawable.collection_home_desk
    "campaign-researcher-home-v1:shelves" -> R.drawable.collection_home_shelves
    "campaign-kingdom-map-v1:table" -> R.drawable.collection_map_table
    "campaign-kingdom-map-v1:copies" -> R.drawable.collection_map_copies
    "campaign-kingdom-map-v1:atlas" -> R.drawable.collection_map_atlas
    "campaign-kingdom-map-v1:field-kit" -> R.drawable.collection_map_field_kit
    "campaign-great-expedition-v1:backpack" -> R.drawable.collection_expedition_backpack
    "campaign-great-expedition-v1:compass" -> R.drawable.collection_expedition_compass
    "campaign-great-expedition-v1:light" -> R.drawable.collection_expedition_light
    "campaign-great-expedition-v1:supplies" -> R.drawable.collection_expedition_supplies
    "campaign-great-expedition-v1:transport" -> R.drawable.collection_expedition_transport
    else -> null
}
