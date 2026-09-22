package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage

@Composable
internal fun EconomyScreen(state: EconomyUiState, onAction: (EconomyAction) -> Unit, onBack: () -> Unit) {
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
        when {
            planning?.stage == BudgetPlanningStage.RECEIPT -> WeeklyIncomeScreen(
                state = WeeklyIncomeUiState(amount = planning.income,
                    header = if (planning.reason == BudgetPlanningReason.INITIAL) "Первый бюджет" else "Новая неделя",
                    title = if (planning.reason == BudgetPlanningReason.INITIAL) "Монеты для приключения" else "Новый запас на неделю"),
                onPlan = { if (!state.saving) onAction(EconomyAction.StartAllocation) }, onBack = onBack)
            else -> BudgetPlanScreen(
                state = BudgetUiState(economy.plan.needs, economy.plan.wants, economy.plan.savings,
                    economy.plan.reserve, economy.unallocated, planning?.income ?: 0, actionsEnabled = !state.saving,
                    minimumNeeds = ru.nksk.lctapp.domain.economy.EconomyOperations.minimumNeeds(economy)),
                onAmountChange = { article, amount -> onAction(EconomyAction.SetAmount(article, amount)) },
                onConfirm = { onAction(EconomyAction.Confirm) }, onBack = onBack,
                onAdjust = { article, increase -> onAction(EconomyAction.Adjust(article, increase)) })
        }
    }
    state.error?.let { error ->
        AlertDialog(onDismissRequest = { onAction(EconomyAction.DismissError) },
            title = { Text("Бюджет не сохранён") }, text = { Text(error) },
            confirmButton = { TextButton(onClick = { onAction(EconomyAction.Retry) }) { Text("Повторить") } },
            dismissButton = { TextButton(onClick = { onAction(EconomyAction.DismissError) }) { Text("Закрыть") } })
    }
}
