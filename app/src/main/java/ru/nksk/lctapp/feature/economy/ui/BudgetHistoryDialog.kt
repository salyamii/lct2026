package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** Only reconciled receipts may explain a changed balance; a difference is not an expense. */
@Composable
internal fun BudgetHistoryDialog(state: EconomyUiState, onClose: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk,
        title = { Text("Что изменилось", fontFamily = Rubik, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    state.budgetHistoryLoading -> {
                        CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally), color = GameInk)
                        Text("Смотрим записи после сохранения плана…", fontFamily = Nunito, fontSize = 16.sp)
                    }
                    state.budgetHistoryError != null -> Text(state.budgetHistoryError,
                        fontFamily = Nunito, fontSize = 16.sp, lineHeight = 23.sp)
                    else -> state.budgetHistory?.let { history ->
                        Text("После сохранения этого плана", fontFamily = Nunito, fontSize = 15.sp)
                        HistoryHeading("Монеты под рукой")
                        HistoryAmount("Было", history.startingAvailable)
                        if (history.income > 0) HistoryAmount("Получили", history.income, "+")
                        if (history.spentAvailable > 0) HistoryAmount("Потратили", history.spentAvailable, "−")
                        if (history.deposited > 0) HistoryAmount("Положили в копилку", history.deposited, "−")
                        if (history.withdrawn > 0) HistoryAmount("Взяли из копилки", history.withdrawn, "+")
                        HorizontalDivider(color = GameInk.copy(alpha = .15f))
                        HistoryAmount("Сейчас доступно", history.resultingAvailable, strong = true)
                        Spacer(Modifier.height(4.dp))
                        HistoryHeading("В копилке")
                        HistoryAmount("Было", history.startingSavings)
                        if (history.deposited > 0) HistoryAmount("Положили", history.deposited, "+")
                        if (history.withdrawn > 0) HistoryAmount("Взяли обратно", history.withdrawn, "−")
                        if (history.spentSavings > 0) HistoryAmount("Купили для цели", history.spentSavings, "−")
                        HorizontalDivider(color = GameInk.copy(alpha = .15f))
                        HistoryAmount("Сейчас в копилке", history.resultingSavings, strong = true)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClose, colors = ButtonDefaults.textButtonColors(contentColor = GameInk)) {
                Text("Понятно", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            if (state.budgetHistoryError != null) TextButton(onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = GameInk)) {
                Text("Повторить", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
    )
}

@Composable
private fun HistoryHeading(text: String) {
    Text(text, color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

@Composable
private fun HistoryAmount(label: String, amount: Long, sign: String = "", strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
        Text("$sign$amount", color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp, lineHeight = 22.sp)
    }
}
