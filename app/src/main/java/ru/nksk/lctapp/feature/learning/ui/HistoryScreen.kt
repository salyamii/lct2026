package ru.nksk.lctapp.feature.learning.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.AdventureHeading
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.game.BudgetHistoryDetails
import ru.nksk.lctapp.core.ui.game.BudgetHistoryUi
import ru.nksk.lctapp.core.ui.game.asGameUiText

@Composable
internal fun HistoryScreen(state: HistoryUiState, onRetry: () -> Unit, onBack: () -> Unit,
    title: String = "История приключения", onArchives: (() -> Unit)? = null) {
    LearningPage(title, onBack) {
        learningStatus(state.loading, false, state.error, onRetry = onRetry)
        onArchives?.let { open -> item { PracticeButton("Прошлые приключения", true, primary = false, onClick = open) } }
        if (!state.loading && state.hasPlans) item {
            CoinMovementHistory(state.coinMovements)
        }
        if (state.periods.isEmpty() && state.operations.isEmpty() && !state.loading) item {
            LearningCard("Первые страницы впереди") {
                AdventureBody("Здесь сохранятся события приключения, покупки и монеты, которые мы отложим.")
            }
        }
        items(state.periods) { period ->
            var expanded by rememberSaveable(period.title) { mutableStateOf(false) }
            LearningCard(period.title) {
                AdventureBody(period.body.asGameUiText())
                PracticeButton(if (expanded) "Свернуть подробности" else "План, траты и причины изменений", true, primary = false) {
                    expanded = !expanded
                }
                if (expanded) {
                    if (period.plan.isNotEmpty()) AdventureBody(period.plan.asGameUiText())
                    period.actual.forEach { AdventureBody(it.asGameUiText()) }
                    period.note?.let { AdventureBody(it.asGameUiText()) }
                    period.comparisons.forEach { comparison ->
                        HorizontalDivider(color = PracticeBorder)
                        Text(comparison.title.asGameUiText(), color = GameInk, style = MaterialTheme.typography.titleMedium)
                        AdventureBody(comparison.note.asGameUiText())
                        comparison.rows.forEach { AdventureBody(it.asGameUiText()) }
                    }
                }
            }
        }
        if (state.operations.isNotEmpty()) item { AdventureHeading("Последние события") }
        items(state.operations) { operation -> LearningCard { AdventureBody(operation.asGameUiText()) } }
    }
}

@Composable
private fun CoinMovementHistory(history: BudgetHistoryUi?) {
    var expanded by rememberSaveable(history?.planId) { mutableStateOf(false) }
    LearningCard("Движение монет") {
        PracticeButton(if (expanded) "Свернуть" else "Откуда пришли и куда ушли монеты", true, primary = false) {
            expanded = !expanded
        }
        if (expanded) {
            if (history != null) {
                AdventureBody("С последнего сохранения плана.")
                BudgetHistoryDetails(history)
            } else {
                AdventureBody("Не хватает записей, чтобы точно показать движение монет. Сохранённые события можно посмотреть ниже.")
            }
        }
    }
}

