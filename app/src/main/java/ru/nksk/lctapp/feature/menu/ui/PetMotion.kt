package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import ru.nksk.lctapp.core.ui.components.MovingPetArtwork

@Composable
internal fun MovingPet(
    artwork: Int,
    description: String,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    MovingPetArtwork(artwork, description, intensity, modifier.testTag("menu_pet"))
}
