package ru.nksk.lctapp.feature.tasks.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.R

internal val defaultMemoryPairArtwork = listOf(
    R.drawable.deed_pair_key,
    R.drawable.deed_pair_armillary,
    R.drawable.deed_pair_star_plate,
    R.drawable.deed_pair_astrolabe,
    R.drawable.deed_pair_tag,
    R.drawable.deed_pair_telescope,
    R.drawable.deed_pair_loupe,
    R.drawable.deed_pair_backpack,
)

/** Presentation labels for the bundled pictures; they do not determine matching or rewards. */
@StringRes
internal fun memoryPairNameResource(@DrawableRes artwork: Int): Int? = when (artwork) {
    R.drawable.deed_pair_key -> R.string.deeds_pair_key
    R.drawable.deed_pair_armillary -> R.string.deeds_pair_armillary
    R.drawable.deed_pair_star_plate, R.drawable.event_star_plate_1 -> R.string.deeds_pair_star_plate
    R.drawable.deed_pair_astrolabe -> R.string.deeds_pair_astrolabe
    R.drawable.deed_pair_tag -> R.string.deeds_pair_tag
    R.drawable.deed_pair_telescope -> R.string.deeds_pair_telescope
    R.drawable.deed_pair_loupe -> R.string.deeds_pair_loupe
    R.drawable.deed_pair_backpack -> R.string.deeds_pair_backpack
    R.drawable.deed_goods_lens -> R.string.deeds_pair_lens
    R.drawable.deed_goods_lantern -> R.string.deeds_pair_lantern
    R.drawable.story_cargo_journal -> R.string.deeds_pair_cargo_journal
    R.drawable.story_observation_journal -> R.string.deeds_pair_observation_journal
    R.drawable.story_letter -> R.string.deeds_pair_letter
    R.drawable.story_old_photograph -> R.string.deeds_pair_old_photograph
    R.drawable.story_instruction_journal -> R.string.deeds_pair_instruction_journal
    R.drawable.story_route_map -> R.string.deeds_pair_route_map
    R.drawable.story_map_missing_region -> R.string.deeds_pair_map_missing_region
    R.drawable.story_assembled_map -> R.string.deeds_pair_assembled_map
    R.drawable.story_route_fragment_tower -> R.string.deeds_pair_route_fragment_tower
    R.drawable.story_route_fragment_mountain -> R.string.deeds_pair_route_fragment_mountain
    R.drawable.story_route_fragment_winding -> R.string.deeds_pair_route_fragment_winding
    R.drawable.story_route_fragment_forest -> R.string.deeds_pair_route_fragment_forest
    R.drawable.story_bridge_token -> R.string.deeds_pair_bridge_token
    R.drawable.story_tower_token -> R.string.deeds_pair_tower_token
    R.drawable.story_route_token -> R.string.deeds_pair_route_token
    R.drawable.story_chronoscope_ring -> R.string.deeds_pair_chronoscope_ring
    else -> null
}
