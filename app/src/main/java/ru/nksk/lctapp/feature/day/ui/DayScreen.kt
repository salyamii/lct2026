package ru.nksk.lctapp.feature.day.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.*

@Composable
internal fun DayScreen(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit) {
    if (state.loading || state.failed) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) CircularProgressIndicator() else {
                    Text("Не удалось загрузить игру. Сохранение не изменено.")
                    Button({ onAction(DayAction.Retry) }) { Text("Повторить") }
                }
                TextButton(onBack) { Text("Назад") }
            }
        }
        return
    }
    GameCardLayout(state.category, gameScene(state.scene), gameCharacter(state.character), onBack) {
        GameBody(state.status)
        state.weeklyReminder?.let { GameBody(it) }
        GameTitle(state.title)
        GameBody(state.body)
        if (state.impact.isNotBlank()) GameBody(state.impact)
        if (state.effort.isNotBlank()) GameBody(state.effort)
        state.message?.let { GameBody(it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.options.any { it.needsFood }) {
            GameButton("Покормить", !state.busy) { onAction(DayAction.ShowMeals) }
        }
        state.options.filterNot { it.needsFood }.forEach { option ->
            GameButton(if (option.needsFood) "Покормить" else option.label, !state.busy && option.enabled) {
                onAction(if (option.needsFood) DayAction.ShowMeals else DayAction.Choose(option.id))
            }
        }
        state.primary?.let { text ->
            GameButton(text, !state.busy) { onAction(if (state.primaryNeedsFood) DayAction.ShowMeals else DayAction.Primary) }
        }
        state.later?.let { text ->
            OutlinedButton(
                { onAction(DayAction.Later) }, Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = GameInk, disabledContentColor = GameInk.copy(alpha = 0.38f),
                ),
            ) { Text(text) }
        }
        if (state.footer.isNotBlank()) GameBody(state.footer)
    }
    if (state.showMeals) AlertDialog(
        onDismissRequest = { if (!state.busy) onAction(DayAction.CloseMeals) },
        title = { Text("Рыжик проголодался") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Нужно поесть. Если деньги на нужды закончились, придётся взять из накоплений. В следующий раз спланируем бюджет внимательнее. Еда не восстанавливает силы.")
                state.message?.let { Text(it) }
                state.meals.forEach { meal -> GameButton(meal.label, meal.enabled && !state.busy) { onAction(DayAction.Feed(meal.id)) } }
            }
        },
        confirmButton = { TextButton({ onAction(DayAction.CloseMeals) }, enabled = !state.busy) { Text("Вернуться") } },
    )
}
