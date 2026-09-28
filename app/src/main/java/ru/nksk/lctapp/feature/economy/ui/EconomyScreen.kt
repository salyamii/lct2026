package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage

@Composable
internal fun EconomyScreen(state: EconomyUiState, onAction: (EconomyAction) -> Unit, onBack: () -> Unit,
    onOpenSavings: () -> Unit = {}) {
    val economy = state.economy
    if (economy == null) {
        Surface(Modifier.fillMaxSize(), color = GamePaper) {
            Column(Modifier.safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (state.loading) CircularProgressIndicator()
                else Button(onClick = { onAction(EconomyAction.Retry) }) { Text("Повторить загрузку") }
                TextButton(onClick = onBack) { Text("В меню") }
            }
        }
    } else {
        val planning = economy.planning
        val display = state.budgetScreenState()
        when {
            planning?.stage == BudgetPlanningStage.RECEIPT && state.budgetConfirmation == null -> WeeklyIncomeScreen(
                state = WeeklyIncomeUiState(amount = planning.income,
                    availableBalance = economy.availableBalance, savingsBalance = economy.savingsBalance,
                    header = if (planning.reason == BudgetPlanningReason.INITIAL) "Первый бюджет" else "Новая неделя",
                    title = if (planning.reason == BudgetPlanningReason.INITIAL) "Монеты для приключения" else "Новый запас на неделю"),
                onPlan = { if (!state.saving) onAction(EconomyAction.StartAllocation) }, onBack = onBack)
            else -> BudgetPlanScreen(
                state = display.budget,
                interactionsBlocked = state.budgetConfirmation != null,
                onAmountChange = { article, amount -> onAction(EconomyAction.SetAmount(article, amount)) },
                onConfirm = { onAction(EconomyAction.Confirm) }, onBack = onBack,
                onAdjust = { article, increase -> onAction(EconomyAction.Adjust(article, increase)) },
                onOpenSavings = onOpenSavings,
                pet = display.pet,
                contextId = display.contextId,
                onContextPresented = { onAction(EconomyAction.ContextPresented(it)) })
        }
    }
    EconomyFeedback(state, onAction)
    if (state.budgetHistoryVisible) BudgetHistoryDialog(state,
        onClose = { onAction(EconomyAction.CloseBudgetHistory) },
        onRetry = { onAction(EconomyAction.OpenBudgetHistory) })
}

@Composable
internal fun EconomyFeedback(state: EconomyUiState, onAction: (EconomyAction) -> Unit,
    showDepositWarning: Boolean = true) {
    val dialogButtonColors = ButtonDefaults.textButtonColors(
        contentColor = GameInk, disabledContentColor = GameInk.copy(alpha = .55f),
    )
    state.depositWarning?.takeIf { showDepositWarning }?.let { warning ->
        AlertDialog(onDismissRequest = { if (!state.saving) onAction(EconomyAction.CancelDepositRisk) },
            containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk, iconContentColor = GameInk,
            title = { Text("На еду может не хватить", fontFamily = Rubik, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, lineHeight = 28.sp) },
            text = { Text("После перевода останется ${coinAmount(warning.remainingBalance)}, а на еду до следующей недели нужно ${warning.neededForFood}. Всё равно отложить?",
                fontFamily = Nunito, fontSize = 16.sp, lineHeight = 24.sp) },
            confirmButton = {
                TextButton(onClick = { onAction(EconomyAction.ConfirmDepositRisk) }, enabled = !state.saving,
                    colors = dialogButtonColors) {
                    Text("Всё равно отложить", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(EconomyAction.CancelDepositRisk) }, enabled = !state.saving,
                    colors = dialogButtonColors) {
                    Text("Отмена", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            })
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = { onAction(EconomyAction.DismissError) },
            containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk, iconContentColor = GameInk,
            title = { Text("Действие не сохранено", fontFamily = Rubik, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, lineHeight = 28.sp) },
            text = { Text(error, fontFamily = Nunito, fontSize = 16.sp, lineHeight = 24.sp) },
            confirmButton = {
                TextButton(onClick = { onAction(EconomyAction.Retry) }, colors = dialogButtonColors) {
                    Text("Повторить", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(EconomyAction.DismissError) }, colors = dialogButtonColors) {
                    Text("Закрыть", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            })
    }
}
