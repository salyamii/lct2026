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
import ru.nksk.lctapp.core.ui.game.BudgetHistoryDetails
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
                        BudgetHistoryDetails(history)
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
