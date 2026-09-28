package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.AdventureHeading
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.theme.AdventureNight

internal val PracticeBorder = Color(0xFFDBD7E9)
internal val PracticeDisabled = Color(0xFFEAE6F0)

@Composable
internal fun LearningPage(title: String, onBack: () -> Unit, backEnabled: Boolean = true,
    listState: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding().background(GamePaper),
        contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxSize(), state = listState, contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                TextButton(onClick = onBack, enabled = backEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = GameInk, disabledContentColor = GameInk),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp)) { Text("Назад") }
                AdventureHeading(title)
            }
            content()
        }
    }
}

internal fun LazyListScope.learningStatus(loading: Boolean, busy: Boolean, error: String?,
    retryRequired: Boolean = false, showSaving: Boolean = true, onRetry: () -> Unit) {
    if (loading || busy && showSaving) item {
        LearningCard {
            GameLoadingIndicator(Modifier.fillMaxWidth(), size = 48.dp)
            AdventureBody(if (loading) "Открываем страницы приключения…" else "Сохраняем…")
        }
    }
    val message = error ?: "Сначала сохраним предыдущий шаг. Повтори попытку, чтобы продолжить."
        .takeIf { retryRequired }
    message?.let { text -> item {
        LearningCard("Не получилось завершить действие") {
            AdventureBody(text.asGameUiText())
            PracticeButton("Повторить", !busy, onClick = onRetry)
        }
    } }
}

@Composable
internal fun BudgetReminder(needsPlan: Boolean, enabled: Boolean, onOpenBudget: () -> Unit) {
    LearningCard(if (needsPlan) "Сначала завершим план" else "Теперь уточним план") {
        AdventureBody(if (needsPlan) "Мы ещё не закончили распределять монеты. Подтверди план, чтобы продолжить разбор."
            else "Мы выяснили, что изменилось. Распредели оставшиеся монеты на то, что понадобится дальше, и подтверди план.")
        PracticeButton(if (needsPlan) "Завершить план" else "Изменить план", enabled, onClick = onOpenBudget)
    }
}

@Composable
internal fun LearningCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, contentColor = GameInk,
        shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, PracticeBorder)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it.asGameUiText(), color = GameInk, style = MaterialTheme.typography.titleLarge) }
            content()
        }
    }
}

@Composable
internal fun PracticeButton(text: String, enabled: Boolean, primary: Boolean = true,
    interactionBlocked: Boolean = false, onClick: () -> Unit) {
    GameActionButton(text, onClick, enabled = enabled, interactionBlocked = interactionBlocked,
        style = if (primary) GameActionStyle.PRIMARY else GameActionStyle.SECONDARY,
        shape = RoundedCornerShape(20.dp), borderColor = PracticeBorder,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        textStyle = MaterialTheme.typography.bodyLarge)
}

