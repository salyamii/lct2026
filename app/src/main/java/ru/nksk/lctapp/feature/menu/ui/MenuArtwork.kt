package ru.nksk.lctapp.feature.menu.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import ru.nksk.lctapp.core.ui.components.GameArtwork

@Composable
internal fun MenuArtwork(@DrawableRes resource: Int, size: Dp) {
    GameArtwork(resource, contentDescription = null, modifier = Modifier.size(size))
}
