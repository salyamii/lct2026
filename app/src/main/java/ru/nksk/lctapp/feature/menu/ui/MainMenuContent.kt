package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R

@Composable
internal fun MainMenuContent(state: MainMenuLoadState, onRetry: () -> Unit, onAction: (MainMenuAction) -> Unit,
    onFreeMeal: () -> Unit, onDismissMeal: () -> Unit,
    settingsButton: (@Composable () -> Unit)? = null) {
    when (state) {
        is MainMenuLoadState.Ready -> MainMenuScreen(state = state.menu, onAction = onAction, settingsButton = settingsButton)
        else -> Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is MainMenuLoadState.Loading) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.game_loading))
                } else {
                    Text(stringResource(R.string.game_load_error))
                    Button(onClick = onRetry) { Text(stringResource(R.string.game_retry)) }
                }
            }
        }
    }
    if (state is MainMenuLoadState.Ready && state.menu.showFreeMeal) {
        AlertDialog(
            onDismissRequest = { if (!state.menu.busy) onDismissMeal() },
            title = { Text("Бесплатная столовая") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("На обычный обед не хватает монет. После бесплатного обеда сегодня понадобится отдых. Утром ${state.menu.pet.name} будет немного уставшим.")
                    if ((state.menu.budget?.actualSavings ?: 0) > 0) {
                        Text("В копилке: ${state.menu.budget?.actualSavings}. Открой её через бюджет, чтобы взять на еду.")
                        TextButton(onClick = { onDismissMeal(); onAction(MainMenuAction.Coins) }, enabled = !state.menu.busy) {
                            Text("Открыть бюджет")
                        }
                    }
                    state.menu.notice?.let { Text(it) }
                }
            },
            confirmButton = { Button(onFreeMeal, enabled = !state.menu.busy) { Text("Поесть бесплатно") } },
            dismissButton = { TextButton(onDismissMeal, enabled = !state.menu.busy) { Text("Вернуться") } },
        )
    }
}
