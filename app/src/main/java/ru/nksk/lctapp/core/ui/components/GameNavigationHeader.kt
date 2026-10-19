package ru.nksk.lctapp.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Keeps navigation reachable without squeezing scene titles between the two controls. */
@Composable
internal fun GameNavigationHeader(
    title: String,
    onBack: () -> Unit,
    onHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    homeEnabled: Boolean = true,
    showBack: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val separateTitle = title.isNotBlank() && onHome != null &&
            (maxWidth < 400.dp || LocalDensity.current.fontScale >= 1.3f)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showBack) GameBackButton(onBack, enabled = backEnabled)
                if (title.isNotBlank() && !separateTitle) {
                    GameSceneTitle(title, Modifier.weight(1f))
                } else Spacer(Modifier.weight(1f))
                trailing?.invoke()
                onHome?.let { GameHomeButton(it, enabled = homeEnabled) }
            }
            if (separateTitle) {
                GameSceneTitle(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}
