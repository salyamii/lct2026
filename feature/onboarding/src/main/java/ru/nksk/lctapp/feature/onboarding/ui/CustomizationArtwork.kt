package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

@Immutable
data class CustomizationArtwork(
    val background: Int,
    val chevron: Int,
    val copper: Int,
    val sand: Int,
    val russet: Int,
    val titleFont: FontFamily,
    val bodyFont: FontFamily,
) {
    fun image(fur: CharacterFur): Int = when (fur) {
        CharacterFur.Copper -> copper
        CharacterFur.Sand -> sand
        CharacterFur.Russet -> russet
    }
}
