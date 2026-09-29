package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingScreen
import ru.nksk.lctapp.core.ui.components.MealSelectionDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
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
    onMeal: (String) -> Unit, onDismissMeal: () -> Unit,
    settingsButton: (@Composable () -> Unit)? = null) {
    when (state) {
        is MainMenuLoadState.Ready -> MainMenuScreen(state = state.menu, onAction = onAction, settingsButton = settingsButton)
        MainMenuLoadState.Loading -> GameLoadingScreen()
        else -> Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.game_load_error))
                Button(onClick = onRetry) { Text(stringResource(R.string.game_retry)) }
            }
        }
    }
    if (state is MainMenuLoadState.Ready && state.menu.showMeals) {
        MealSelectionDialog(state.menu.meals, state.menu.busy,
            onChoose = onMeal, onDismiss = onDismissMeal, message = state.menu.notice,
            extraContent = {
                if (state.menu.showFreeMeal && (state.menu.budget?.actualSavings ?: 0) > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("В копилке: ${state.menu.budget?.actualSavings}")
                        TextButton(onClick = { onDismissMeal(); onAction(MainMenuAction.Coins) }, enabled = !state.menu.busy) {
                            Text("Открыть бюджет")
                        }
                    }
                }
            },
        )
    }
}
