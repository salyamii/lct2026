package ru.nksk.lctapp.feature.map.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import ru.nksk.lctapp.core.ui.components.GameHomeButton
import ru.nksk.lctapp.core.ui.components.GameBackButton
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MapContent(state: MapUiState, onSelect: (String) -> Unit, onBack: () -> Unit,
    onRetry: () -> Unit, onDismissError: () -> Unit, onHome: () -> Unit = onBack) {
        if (state.locations.isNotEmpty()) {
            LocationMapScreen(state.locations, state.selectedId,
                onLocationClick = { onSelect(it) },
                onBack = onBack, onHome = onHome, interactionEnabled = !state.saving)
        } else {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GameBackButton(onBack, enabled = !state.saving)
                    GameHomeButton(onHome, enabled = !state.saving)
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.loading) GameLoadingIndicator()
                    else {
                        Text(state.error ?: "Карта недоступна")
                        TextButton(onClick = onRetry) { Text("Повторить") }
                    }
                }
            }
        }
        if (state.error != null && state.locations.isNotEmpty()) AlertDialog(
            onDismissRequest = onDismissError,
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("Понятно") } },
        )
}
