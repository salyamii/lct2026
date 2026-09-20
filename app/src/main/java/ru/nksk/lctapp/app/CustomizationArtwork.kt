package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.feature.onboarding.ui.CustomizationArtwork

@Composable
internal fun customizationArtwork(): CustomizationArtwork {
    val resources = LocalContext.current.resources
    // Layoutlib can retain a synthetic R class after adding an asset or changing branches.
    @Suppress("DiscouragedApi")
    val background = if (LocalInspectionMode.current) remember(resources) {
        resources.getIdentifier("customization_courtyard", "drawable", "ru.nksk.lctapp").also {
            check(it != 0) { "Refresh the Preview resource index: customization_courtyard is missing" }
        }
    } else R.drawable.customization_courtyard
    return CustomizationArtwork(background, R.drawable.menu_chevron,
        R.drawable.ryzhik_cub_body_base_no_accessory,
        R.drawable.ryzhik_cub_body_base_sand,
        R.drawable.ryzhik_cub_body_base_dark_russet, Rubik, Nunito)
}
