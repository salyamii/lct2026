package ru.nksk.lctapp.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ru.nksk.lctapp.R

/** Generated artwork; landmark positions are presentation, not progression rules. */
@Composable
internal fun MapLandscape(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.map_adventure_day),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
