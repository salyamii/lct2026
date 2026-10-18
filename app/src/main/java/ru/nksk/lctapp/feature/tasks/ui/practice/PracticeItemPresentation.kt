package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.PracticeItem

internal val PracticeItem.titleRes: Int
    @StringRes get() = when (this) {
        PracticeItem.MAP -> R.string.practice_item_map
        PracticeItem.WATER -> R.string.practice_item_water
        PracticeItem.BUN -> R.string.practice_item_bun
        PracticeItem.LANTERN -> R.string.practice_item_lantern
        PracticeItem.RINGS -> R.string.practice_item_rings
        PracticeItem.TOY_BOAT -> R.string.practice_item_boat
        PracticeItem.KEYCHAIN -> R.string.practice_item_keychain
        PracticeItem.CAP -> R.string.practice_item_cap
        PracticeItem.BACKPACK -> R.string.practice_item_backpack
        PracticeItem.MEDICINE -> R.string.practice_item_medicine
        PracticeItem.BACKPACK_REPAIR -> R.string.practice_item_repair
        PracticeItem.BREAD -> R.string.practice_item_bread
        PracticeItem.CANDY -> R.string.practice_item_candy
        PracticeItem.PLANK -> R.string.practice_item_plank
        PracticeItem.CANDLES -> R.string.practice_item_candles
        PracticeItem.NAILS -> R.string.practice_item_nails
        PracticeItem.HAMMER -> R.string.practice_item_hammer
        PracticeItem.STAR_MAP -> R.string.practice_item_star_map
        PracticeItem.SCARF -> R.string.practice_item_scarf
        PracticeItem.TEDDY -> R.string.practice_item_teddy
        PracticeItem.FISHING_ROD -> R.string.practice_item_rod
        PracticeItem.BAIT -> R.string.practice_item_bait
        PracticeItem.BUCKET -> R.string.practice_item_bucket
    }

internal val PracticeItem.artwork: Int
    @DrawableRes get() = when (this) {
        PracticeItem.MAP -> R.drawable.story_route_map
        PracticeItem.WATER -> R.drawable.icon_practice_water
        PracticeItem.BUN -> R.drawable.prop_bakery_bun
        PracticeItem.LANTERN -> R.drawable.gear_lantern
        PracticeItem.RINGS -> R.drawable.prop_fair_ring_toss
        PracticeItem.TOY_BOAT -> R.drawable.prop_fair_toy_boat
        PracticeItem.KEYCHAIN -> R.drawable.prop_fair_compass_keychain
        PracticeItem.CAP -> R.drawable.prop_fair_explorer_hat
        PracticeItem.BACKPACK -> R.drawable.prop_practice_backpack
        PracticeItem.MEDICINE -> R.drawable.prop_practice_medicine
        PracticeItem.BACKPACK_REPAIR -> R.drawable.event_backpack_torn
        PracticeItem.BREAD -> R.drawable.prop_practice_bread
        PracticeItem.CANDY -> R.drawable.prop_practice_candy
        PracticeItem.PLANK -> R.drawable.prop_practice_plank
        PracticeItem.CANDLES -> R.drawable.prop_practice_candles
        PracticeItem.NAILS -> R.drawable.prop_practice_nails
        PracticeItem.HAMMER -> R.drawable.prop_practice_hammer
        PracticeItem.STAR_MAP -> R.drawable.gear_star_map
        PracticeItem.SCARF -> R.drawable.icon_practice_scarf
        PracticeItem.TEDDY -> R.drawable.icon_practice_teddy
        PracticeItem.FISHING_ROD -> R.drawable.icon_practice_fishing_rod
        PracticeItem.BAIT -> R.drawable.icon_practice_bait
        PracticeItem.BUCKET -> R.drawable.icon_practice_bucket
    }
