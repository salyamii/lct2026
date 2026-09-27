package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R

/** Existing exact artwork, also used by onboarding; an unknown look is not substituted. */
@DrawableRes
internal fun cosmeticArtwork(lookId: String): Int? = when (lookId) {
    "BANDANA" -> R.drawable.gear_blue_bandana
    "BACKPACK" -> R.drawable.gear_explorer_backpack
    "HAT" -> R.drawable.gear_explorer_hat
    "GLASSES" -> R.drawable.gear_pilot_goggles
    "ROUTE_PATCH" -> R.drawable.gear_route_patch
    "COMPASS" -> R.drawable.gear_compass_medallion
    "BINOCULARS" -> R.drawable.gear_binoculars
    else -> null
}
