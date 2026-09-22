package ru.nksk.lctapp.feature.economy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.components.GameButton
import ru.nksk.lctapp.core.ui.components.GameBody
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

/** Local interaction sandbox only. Never reads or writes the player's game. */
@Composable
internal fun BudgetPreview(initial: BudgetUiState, showIncomeFirst: Boolean = false) {
    var values by rememberSaveable {
        mutableStateOf(longArrayOf(initial.needs, initial.wants, initial.savings, initial.reserve, initial.unallocated))
    }
    var planningStarted by rememberSaveable { mutableStateOf(!showIncomeFirst) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var inMenu by rememberSaveable { mutableStateOf(false) }
    val screenState = rememberSaveableStateHolder()
    val state = BudgetUiState(values[0], values[1], values[2], values[3], values[4], initial.weeklyIncome)
    var exitBlocked by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = {
        if (!planningStarted) inMenu = true
        else if (state.canConfirm) confirmed = true
        else exitBlocked = true
    }
    LCTAppTheme {
        BackHandler(enabled = !inMenu && !confirmed) { back() }
        if (inMenu) {
            Surface(Modifier.fillMaxSize(), color = GamePaper) {
                Column(Modifier.safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    GameBody("Выход в меню · Preview")
                    GameBody(if (planningStarted) "Текущее распределение сохранено. Продолжение вернёт к бюджету."
                        else "Продолжение вернёт к экрану поступления монет.")
                    GameButton("Продолжить день") { inMenu = false }
                }
            }
        } else if (!planningStarted) {
            WeeklyIncomeScreen(
                state = WeeklyIncomeUiState(amount = initial.weeklyIncome),
                onPlan = { planningStarted = true },
                onBack = { inMenu = true },
            )
        } else screenState.SaveableStateProvider("budget") {
            val onAmountChange: (BudgetArticle, Long) -> Unit = { article, amount ->
            val index = article.ordinal
            val available = values[index] + values[4]
            if (amount in 0..available && (amount >= article.minimum ||
                    (values[index] < article.minimum && amount > values[index]))) {
                values = values.copyOf().also { it[index] = amount; it[4] = available - amount }
            }
            }
            BudgetPlanScreen(state, onAmountChange,
                onConfirm = { if (state.canConfirm) confirmed = true }, onBack = back)
        }
        if (exitBlocked) AlertDialog(onDismissRequest = { exitBlocked = false },
            title = { Text("Заверши распределение") },
            text = { Text(if (state.unallocated > 0) "Распредели оставшиеся ${state.unallocated} монет, чтобы продолжить."
                else "Добавь в «Нужно» ещё ${state.minimumNeeds - state.needs} монет, чтобы завершить бюджет.") },
            confirmButton = { TextButton(onClick = { exitBlocked = false }) { Text("Распределить") } })
        if (confirmed) AlertDialog(onDismissRequest = { confirmed = false }, containerColor = GamePaper,
            titleContentColor = GameInk, textContentColor = GameInk,
            title = { Text("Бюджет распределён") },
            text = { Text("Предпросмотр завершён. Игровое сохранение не изменялось.") },
            confirmButton = { TextButton(onClick = { confirmed = false }) { Text("Вернуться", color = GameInk) } })
    }
}
