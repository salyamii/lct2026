package ru.nksk.lctapp.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import ru.nksk.lctapp.R
import ru.nksk.lctapp.feature.onboarding.ui.*

@Composable
internal fun accessoryArtwork(artwork: CustomizationArtwork): AccessoryArtwork = AccessoryArtwork(
    noneIcon = accessoryIcon("customization_none", { R.drawable.customization_none }),
    lockIcon = accessoryIcon("customization_lock", { R.drawable.customization_lock }),
    thumbnails = mapOf(
        OnboardingAccessory.Backpack to R.drawable.gear_explorer_backpack,
        OnboardingAccessory.Bandana to R.drawable.gear_blue_bandana,
        OnboardingAccessory.Lantern to R.drawable.gear_lantern,
        OnboardingAccessory.Patch to R.drawable.gear_route_patch,
        OnboardingAccessory.Hat to R.drawable.gear_explorer_hat,
        OnboardingAccessory.Goggles to R.drawable.gear_pilot_goggles,
        OnboardingAccessory.Compass to R.drawable.gear_compass_medallion,
        OnboardingAccessory.Binoculars to R.drawable.gear_binoculars,
    ),
    portraits = OnboardingAccessory.entries.associateWith { accessory ->
        CharacterFur.entries.associateWith { fur ->
            accessoryPortrait(AccessoryCustomizationUiState(fur = fur, accessory = accessory), artwork)
        }
    },
)

private fun accessoryPortrait(state: AccessoryCustomizationUiState, artwork: CustomizationArtwork): Int {
    fun fur(copper: Int, sand: Int, russet: Int) = when (state.fur) {
        CharacterFur.Copper -> copper
        CharacterFur.Sand -> sand
        CharacterFur.Russet -> russet
    }
    return when (state.accessory) {
        OnboardingAccessory.None -> artwork.image(state.fur)
        OnboardingAccessory.Backpack -> fur(R.drawable.ryzhik_cub_body_accessory_backpack,
            R.drawable.ryzhik_cub_body_accessory_backpack_sand, R.drawable.ryzhik_cub_body_accessory_backpack_dark_russet)
        OnboardingAccessory.Bandana -> fur(R.drawable.ryzhik_cub_body_accessory_bandana,
            R.drawable.ryzhik_cub_body_accessory_bandana_sand, R.drawable.ryzhik_cub_body_accessory_bandana_dark_russet)
        OnboardingAccessory.Lantern -> fur(R.drawable.ryzhik_cub_body_accessory_lantern,
            R.drawable.ryzhik_cub_body_accessory_lantern_sand, R.drawable.ryzhik_cub_body_accessory_lantern_dark_russet)
        OnboardingAccessory.Patch -> fur(R.drawable.ryzhik_cub_body_accessory_route_patch,
            R.drawable.ryzhik_cub_body_accessory_route_patch_sand, R.drawable.ryzhik_cub_body_accessory_route_patch_dark_russet)
        OnboardingAccessory.Hat -> fur(R.drawable.ryzhik_cub_body_accessory_hat,
            R.drawable.ryzhik_cub_body_accessory_hat_sand, R.drawable.ryzhik_cub_body_accessory_hat_dark_russet)
        OnboardingAccessory.Goggles -> fur(R.drawable.ryzhik_cub_body_accessory_goggles,
            R.drawable.ryzhik_cub_body_accessory_goggles_sand, R.drawable.ryzhik_cub_body_accessory_goggles_dark_russet)
        OnboardingAccessory.Compass -> fur(R.drawable.ryzhik_cub_body_accessory_compass,
            R.drawable.ryzhik_cub_body_accessory_compass_sand, R.drawable.ryzhik_cub_body_accessory_compass_dark_russet)
        OnboardingAccessory.Binoculars -> fur(R.drawable.ryzhik_cub_body_accessory_binoculars,
            R.drawable.ryzhik_cub_body_accessory_binoculars_sand, R.drawable.ryzhik_cub_body_accessory_binoculars_dark_russet)
    }
}

@Suppress("DiscouragedApi")
@Composable
private fun accessoryIcon(name: String, runtime: () -> Int): Int =
    if (LocalInspectionMode.current) {
        LocalContext.current.resources.getIdentifier(name, "drawable", "ru.nksk.lctapp").also { check(it != 0) }
    } else runtime()
