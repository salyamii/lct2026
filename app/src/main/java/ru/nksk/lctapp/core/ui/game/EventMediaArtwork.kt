package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.engine.EventMedia

/** New scene descriptors resolve their own art before the legacy catalog adapter is considered. */
internal fun EventMedia.sceneArtwork(description: String): EventSceneArtwork? =
    eventMediaArtwork(artworkKey)?.let { EventSceneArtwork(it, description) }
        ?: characterKey?.let { eventSceneArtwork(eventId = null, characterKey = it) }

/** Platform resource bindings contain semantic keys, never event identities. */
@DrawableRes
internal fun eventMediaArtwork(key: String?): Int? = when (key) {
    "purchase.bakery_bun" -> R.drawable.prop_bakery_bun
    "purchase.explorer_hat" -> R.drawable.prop_fair_explorer_hat
    "purchase.ring_toss" -> R.drawable.prop_fair_ring_toss
    "purchase.compass_keychain" -> R.drawable.prop_fair_compass_keychain
    "purchase.toy_boat" -> R.drawable.prop_fair_toy_boat
    "accessory.explorer_hat" -> R.drawable.gear_explorer_hat
    "accessory.pilot_goggles" -> R.drawable.gear_pilot_goggles
    "accessory.route_patch" -> R.drawable.gear_route_patch
    "accessory.binoculars" -> R.drawable.gear_binoculars
    "goal.stargazing" -> R.drawable.goal_preview_stargazing
    "scene.observatory" -> R.drawable.location_observatory_stage
    "scene.pier_day" -> R.drawable.location_pier_day
    "scene.fair_day" -> R.drawable.location_fair_day
    "scene.trail_day" -> R.drawable.location_trail_day
    "scene.workshop_day" -> R.drawable.location_workshop_day
    "scene.village" -> R.drawable.menu_village
    "deed.goods_lantern" -> R.drawable.deed_goods_lantern
    "deed.goods_lens" -> R.drawable.deed_goods_lens
    "deed.pair_armillary" -> R.drawable.deed_pair_armillary
    "deed.pair_astrolabe" -> R.drawable.deed_pair_astrolabe
    "deed.pair_key" -> R.drawable.deed_pair_key
    "deed.pair_loupe" -> R.drawable.deed_pair_loupe
    "event.star_plate_1" -> R.drawable.event_star_plate_1
    "event.star_plate_tarnished" -> R.drawable.event_star_plate_tarnished
    "story.assembled_map" -> R.drawable.story_assembled_map
    "story.bridge_token" -> R.drawable.story_bridge_token
    "story.cargo_journal" -> R.drawable.story_cargo_journal
    "story.chronoscope_ring" -> R.drawable.story_chronoscope_ring
    "story.instruction_journal" -> R.drawable.story_instruction_journal
    "story.letter" -> R.drawable.story_letter
    "story.map_missing_region" -> R.drawable.story_map_missing_region
    "story.observation_journal" -> R.drawable.story_observation_journal
    "story.old_photograph" -> R.drawable.story_old_photograph
    "story.relay" -> R.drawable.story_relay
    "story.route_fragment_forest" -> R.drawable.story_route_fragment_forest
    "story.route_fragment_mountain" -> R.drawable.story_route_fragment_mountain
    "story.route_fragment_tower" -> R.drawable.story_route_fragment_tower
    "story.route_fragment_winding" -> R.drawable.story_route_fragment_winding
    "story.route_map" -> R.drawable.story_route_map
    "story.route_tester" -> R.drawable.story_route_tester
    "story.route_token" -> R.drawable.story_route_token
    "story.signal_lens" -> R.drawable.story_signal_lens
    "story.tower_token" -> R.drawable.story_tower_token
    else -> null
}
