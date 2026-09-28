package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import ru.nksk.lctapp.R

/** Shared geometry for every full-window stage between the system splash and the menu. */
@Composable
internal fun GameLoadingScreen(modifier: Modifier = Modifier, isPlaying: Boolean = true) {
    // Do not center a column of coin + label, or apply system insets here: both move the coin.
    Box(modifier.fillMaxSize().background(colorResource(R.color.startup_background)),
        contentAlignment = Alignment.Center) {
        GameLoadingIndicator(size = dimensionResource(R.dimen.startup_coin_size), isPlaying = isPlaying)
    }
}
