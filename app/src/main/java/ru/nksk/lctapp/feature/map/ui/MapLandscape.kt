package ru.nksk.lctapp.feature.map.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R

/** Generated artwork; landmark positions are presentation, not progression rules. */
@Composable
internal fun MapLandscape(modifier: Modifier = Modifier) {
    GameArtwork(R.drawable.map_adventure_day,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
    )
}
