package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetColor

/** One saved look selects its exact cap series/color across all verified age and fur variants. */
@DrawableRes
internal fun rewardCapArtwork(lookId: String, age: PetAge, color: PetColor): Int? =
    caps[lookId]?.image(age, color)

private data class FurVariants(
    @param:DrawableRes val copper: Int,
    @param:DrawableRes val sand: Int,
    @param:DrawableRes val darkRusset: Int,
) {
    fun image(color: PetColor): Int = when (color) {
        PetColor.COPPER -> copper
        PetColor.SAND -> sand
        PetColor.DARK_RUSSET -> darkRusset
    }
}

private data class AgeVariants(
    val cub: FurVariants,
    val teen: FurVariants,
    val adult: FurVariants,
    val senior: FurVariants,
) {
    fun image(age: PetAge, color: PetColor): Int = when (age) {
        PetAge.CUB -> cub
        PetAge.TEEN -> teen
        PetAge.ADULT -> adult
        PetAge.SENIOR -> senior
    }.image(color)
}

private val caps = mapOf(
    "CAP_MOSCOW_BLUE" to AgeVariants(
        cub = FurVariants(R.drawable.ryzhik_cub_body_accessory_cap_moscow_blue,
            R.drawable.ryzhik_cub_body_accessory_cap_moscow_blue_sand, R.drawable.ryzhik_cub_body_accessory_cap_moscow_blue_dark_russet),
        teen = FurVariants(R.drawable.ryzhik_teen_body_accessory_cap_moscow_blue,
            R.drawable.ryzhik_teen_body_accessory_cap_moscow_blue_sand, R.drawable.ryzhik_teen_body_accessory_cap_moscow_blue_dark_russet),
        adult = FurVariants(R.drawable.ryzhik_adult_body_accessory_cap_moscow_blue,
            R.drawable.ryzhik_adult_body_accessory_cap_moscow_blue_sand, R.drawable.ryzhik_adult_body_accessory_cap_moscow_blue_dark_russet),
        senior = FurVariants(R.drawable.ryzhik_senior_body_accessory_cap_moscow_blue,
            R.drawable.ryzhik_senior_body_accessory_cap_moscow_blue_sand, R.drawable.ryzhik_senior_body_accessory_cap_moscow_blue_dark_russet),
    ),
    "CAP_MOSCOW_EMERALD" to AgeVariants(
        cub = FurVariants(R.drawable.ryzhik_cub_body_accessory_cap_moscow_emerald,
            R.drawable.ryzhik_cub_body_accessory_cap_moscow_emerald_sand, R.drawable.ryzhik_cub_body_accessory_cap_moscow_emerald_dark_russet),
        teen = FurVariants(R.drawable.ryzhik_teen_body_accessory_cap_moscow_emerald,
            R.drawable.ryzhik_teen_body_accessory_cap_moscow_emerald_sand, R.drawable.ryzhik_teen_body_accessory_cap_moscow_emerald_dark_russet),
        adult = FurVariants(R.drawable.ryzhik_adult_body_accessory_cap_moscow_emerald,
            R.drawable.ryzhik_adult_body_accessory_cap_moscow_emerald_sand, R.drawable.ryzhik_adult_body_accessory_cap_moscow_emerald_dark_russet),
        senior = FurVariants(R.drawable.ryzhik_senior_body_accessory_cap_moscow_emerald,
            R.drawable.ryzhik_senior_body_accessory_cap_moscow_emerald_sand, R.drawable.ryzhik_senior_body_accessory_cap_moscow_emerald_dark_russet),
    ),
    "CAP_MOSCOW_BURGUNDY" to AgeVariants(
        cub = FurVariants(R.drawable.ryzhik_cub_body_accessory_cap_moscow_burgundy,
            R.drawable.ryzhik_cub_body_accessory_cap_moscow_burgundy_sand, R.drawable.ryzhik_cub_body_accessory_cap_moscow_burgundy_dark_russet),
        teen = FurVariants(R.drawable.ryzhik_teen_body_accessory_cap_moscow_burgundy,
            R.drawable.ryzhik_teen_body_accessory_cap_moscow_burgundy_sand, R.drawable.ryzhik_teen_body_accessory_cap_moscow_burgundy_dark_russet),
        adult = FurVariants(R.drawable.ryzhik_adult_body_accessory_cap_moscow_burgundy,
            R.drawable.ryzhik_adult_body_accessory_cap_moscow_burgundy_sand, R.drawable.ryzhik_adult_body_accessory_cap_moscow_burgundy_dark_russet),
        senior = FurVariants(R.drawable.ryzhik_senior_body_accessory_cap_moscow_burgundy,
            R.drawable.ryzhik_senior_body_accessory_cap_moscow_burgundy_sand, R.drawable.ryzhik_senior_body_accessory_cap_moscow_burgundy_dark_russet),
    ),
    "CAP_LCT2026_BLUE" to AgeVariants(
        cub = FurVariants(R.drawable.ryzhik_cub_body_accessory_cap_lct2026_blue,
            R.drawable.ryzhik_cub_body_accessory_cap_lct2026_blue_sand, R.drawable.ryzhik_cub_body_accessory_cap_lct2026_blue_dark_russet),
        teen = FurVariants(R.drawable.ryzhik_teen_body_accessory_cap_lct2026_blue,
            R.drawable.ryzhik_teen_body_accessory_cap_lct2026_blue_sand, R.drawable.ryzhik_teen_body_accessory_cap_lct2026_blue_dark_russet),
        adult = FurVariants(R.drawable.ryzhik_adult_body_accessory_cap_lct2026_blue,
            R.drawable.ryzhik_adult_body_accessory_cap_lct2026_blue_sand, R.drawable.ryzhik_adult_body_accessory_cap_lct2026_blue_dark_russet),
        senior = FurVariants(R.drawable.ryzhik_senior_body_accessory_cap_lct2026_blue,
            R.drawable.ryzhik_senior_body_accessory_cap_lct2026_blue_sand, R.drawable.ryzhik_senior_body_accessory_cap_lct2026_blue_dark_russet),
    ),
    "CAP_LCT2026_EMERALD" to AgeVariants(
        cub = FurVariants(R.drawable.ryzhik_cub_body_accessory_cap_lct2026_emerald,
            R.drawable.ryzhik_cub_body_accessory_cap_lct2026_emerald_sand, R.drawable.ryzhik_cub_body_accessory_cap_lct2026_emerald_dark_russet),
        teen = FurVariants(R.drawable.ryzhik_teen_body_accessory_cap_lct2026_emerald,
            R.drawable.ryzhik_teen_body_accessory_cap_lct2026_emerald_sand, R.drawable.ryzhik_teen_body_accessory_cap_lct2026_emerald_dark_russet),
        adult = FurVariants(R.drawable.ryzhik_adult_body_accessory_cap_lct2026_emerald,
            R.drawable.ryzhik_adult_body_accessory_cap_lct2026_emerald_sand, R.drawable.ryzhik_adult_body_accessory_cap_lct2026_emerald_dark_russet),
        senior = FurVariants(R.drawable.ryzhik_senior_body_accessory_cap_lct2026_emerald,
            R.drawable.ryzhik_senior_body_accessory_cap_lct2026_emerald_sand, R.drawable.ryzhik_senior_body_accessory_cap_lct2026_emerald_dark_russet),
    ),
    "CAP_LCT2026_BURGUNDY" to AgeVariants(
        cub = FurVariants(R.drawable.ryzhik_cub_body_accessory_cap_lct2026_burgundy,
            R.drawable.ryzhik_cub_body_accessory_cap_lct2026_burgundy_sand, R.drawable.ryzhik_cub_body_accessory_cap_lct2026_burgundy_dark_russet),
        teen = FurVariants(R.drawable.ryzhik_teen_body_accessory_cap_lct2026_burgundy,
            R.drawable.ryzhik_teen_body_accessory_cap_lct2026_burgundy_sand, R.drawable.ryzhik_teen_body_accessory_cap_lct2026_burgundy_dark_russet),
        adult = FurVariants(R.drawable.ryzhik_adult_body_accessory_cap_lct2026_burgundy,
            R.drawable.ryzhik_adult_body_accessory_cap_lct2026_burgundy_sand, R.drawable.ryzhik_adult_body_accessory_cap_lct2026_burgundy_dark_russet),
        senior = FurVariants(R.drawable.ryzhik_senior_body_accessory_cap_lct2026_burgundy,
            R.drawable.ryzhik_senior_body_accessory_cap_lct2026_burgundy_sand, R.drawable.ryzhik_senior_body_accessory_cap_lct2026_burgundy_dark_russet),
    ),
)
