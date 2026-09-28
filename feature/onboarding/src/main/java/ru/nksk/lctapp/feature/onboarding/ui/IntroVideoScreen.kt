package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** App supplies the shared player and action component; this feature owns only the screen layout. */
@Composable
fun IntroVideoScreen(
    playbackFailed: Boolean,
    video: @Composable (Modifier) -> Unit,
    soundToggle: @Composable (Modifier) -> Unit,
    continueAction: @Composable (Modifier) -> Unit,
    settingsStatus: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!playbackFailed) video(Modifier.fillMaxSize())
        else Column(
            Modifier.align(Alignment.Center).safeDrawingPadding().widthIn(max = 480.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Не удалось открыть вступление", color = Color.White,
                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Можно продолжить и познакомиться со спутником.", color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(20.dp).widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            settingsStatus()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                soundToggle(Modifier.weight(1f))
                continueAction(Modifier.weight(1f))
            }
        }
    }
}
