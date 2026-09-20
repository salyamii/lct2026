package ru.nksk.lctapp.feature.map.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun MapContent(state: MapUiState, onSelect: (String) -> Unit, onBack: () -> Unit,
    onRetry: () -> Unit, onDismissError: () -> Unit) {
        if (state.locations.isNotEmpty()) {
            LocationMapScreen(state.locations, state.selectedId,
                onLocationClick = { onSelect(it) },
                onBack = onBack, interactionEnabled = !state.saving)
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                if (state.loading) CircularProgressIndicator()
                else {
                    Text(state.error ?: "Карта недоступна")
                    TextButton(onClick = onRetry) { Text("Повторить") }
                }
                TextButton(onClick = onBack) { Text("Назад") }
            }
        }
        if (state.error != null && state.locations.isNotEmpty()) AlertDialog(
            onDismissRequest = onDismissError,
            text = { Text(state.error.orEmpty()) },
            confirmButton = { TextButton(onClick = onDismissError) { Text("Понятно") } },
        )
}
