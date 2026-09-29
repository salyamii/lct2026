package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DeedGameHost(state: DeedGameUiState, onRetry: () -> Unit, onBack: () -> Unit,
    onSkipGame: () -> Unit = {},
    content: @Composable () -> Unit) {
    if (state.presentation == null) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) GameLoadingIndicator()
                state.message?.let { Text(it) }
                if (state.canRetry) Button(onRetry, enabled = !state.busy) { Text("Повторить") }
                TextButton(onBack) { Text("В главное меню") }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().then(if (state.demoMode) Modifier.statusBarsPadding() else Modifier)) {
            if (state.demoMode) Surface(color = DeedColors.Cream) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Режим бога", style = MaterialTheme.typography.labelMedium)
                    TextButton(onSkipGame, enabled = state.canSkipGame && !state.busy) { Text("Пропустить игру") }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
                if (state.busy || state.message != null) Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = DeedColors.Cream,
                ) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        if (state.busy) GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
                        state.message?.let { Text(it, color = DeedColors.Text) }
                        if (state.canRetry) Button(onRetry, enabled = !state.busy) { Text("Повторить") }
                    }
                }
            }
        }
    }
}
