package ru.nksk.lctapp.feature.learning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectableGroup
import ru.nksk.lctapp.core.ui.components.GameQuizOption
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.AdventureHeading
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind

private val PracticeBorder = Color(0xFFDBD7E9)
private val PracticeDisabled = Color(0xFFEAE6F0)

@Composable
internal fun LearningScreen(state: LearningUiState, onAction: (LearningAction) -> Unit, onBack: () -> Unit,
    onOpenBudget: () -> Unit = {}, mode: LearningPageMode = LearningPageMode.TRAINING) {
    if (mode == LearningPageMode.REFLECTION || state.chronoscopeStep != null) {
        if (state.chronoscopeStep != null) ChronoscopeScreen(state, onAction, onBack)
        else LearningPage("А что, если…", onBack) { learningStatus(state, onAction) }
        return
    }
    if (mode == LearningPageMode.HISTORY) {
        LearningHistoryScreen(state, onAction, onBack)
        return
    }
    val question = state.question.takeIf { state.practiceOpen }
    val closeQuestion = {
        if (!state.busy) {
            if (state.needsBudgetPlanning || state.practiceRetryRequired) onBack()
            else onAction(LearningAction.CloseQuestion)
        }
    }
    BackHandler(enabled = question != null) { closeQuestion() }
    if (question != null) {
        key(question.id) { PracticeQuestionScreen(state, question, onAction, closeQuestion, onOpenBudget) }
    } else LearningPage("Тренировка навыков", onBack) {
        learningStatus(state, onAction)
        if (!state.loading && state.realGame != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GameArtwork(R.drawable.menu_tasks, null, Modifier.size(76.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Разберёмся с монетами", color = GameInk, style = MaterialTheme.typography.titleMedium)
                        AdventureBody("Выбери тему и разбирайся с монетами в своём темпе.")
                    }
                }
            }
            if (state.needsBudgetPlanning || state.needsBudgetRevision) item {
                BudgetReminder(state.needsBudgetPlanning, !state.busy && !state.practiceRetryRequired, onOpenBudget)
            }
            val enabled = state.canReview && !state.busy && !state.practiceRetryRequired
            if (!state.canReview && !state.needsBudgetPlanning) item {
                AdventureBody("Сначала выберем, что собрать для большого приключения.")
            }
            FinancialQuestionKind.entries.forEach { kind -> item {
                val current = state.question?.takeIf { it.kind == kind && it.series != null && !(it.correct && it.series?.isLast == true) }
                TrainingTopic(kind, current?.series?.questionNumber, enabled) { onAction(kind.startAction()) }
            } }
        }
    }
}

@Composable
private fun TrainingTopic(kind: FinancialQuestionKind, resumeAt: Int?, enabled: Boolean, onClick: () -> Unit) {
    val tint = when (kind) {
        FinancialQuestionKind.PLAN_REVIEW -> Color(0xFFEAF4FC)
        FinancialQuestionKind.CONSEQUENCE -> Color(0xFFFFF0DF)
        FinancialQuestionKind.TRANSACTION_ACCOUNTING -> Color(0xFFF0ECFA)
        FinancialQuestionKind.SAVING_PRACTICE -> Color(0xFFEEF5D9)
    }
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp), color = tint, contentColor = GameInk,
        border = BorderStroke(1.dp, PracticeBorder)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GameArtwork(kind.trainingArtwork(), null, Modifier.size(58.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(kind.trainingTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(resumeAt?.let { "Продолжить тренировку" } ?: kind.trainingDescription(),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", fontSize = 30.sp)
        }
    }
}

@Composable
private fun PracticeQuestionScreen(state: LearningUiState, question: FinancialQuestion,
    onAction: (LearningAction) -> Unit, onBack: () -> Unit, onOpenBudget: () -> Unit) {
    var retrying by rememberSaveable(question.id, question.attempts) { mutableStateOf(false) }
    val series = question.series
    val advancing = question.correct && series != null
    var selectedAnswer by rememberSaveable(question.id, question.attempts) { mutableStateOf<String?>(null) }
    val showExplanation = question.answeredOptionId != null && !retrying
    val enabled = !state.busy && !state.needsBudgetPlanning && !state.practiceRetryRequired
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val questionExposure = remember(question.id, question.prompt, question.options) {
        QuestionExposure(question.options.map { it.id })
    }
    val questionCoordinates = remember(questionExposure) { mutableMapOf<String, LayoutCoordinates>() }
    val showingQuestion = !advancing && !showExplanation
    fun recordQuestionPart(part: String, coordinates: LayoutCoordinates) {
        questionCoordinates[part] = coordinates
        if (showingQuestion && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            questionExposure.record(part, coordinates.fullyVisibleForPractice())) {
            onAction(LearningAction.QuestionPresented(question.id))
        }
    }
    LaunchedEffect(showingQuestion, lifecycleState, questionExposure) {
        if (showingQuestion && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            questionCoordinates.toList().forEach { (part, coordinates) -> recordQuestionPart(part, coordinates) }
        }
    }
    LaunchedEffect(question.id, advancing, enabled) {
        if (advancing && enabled && state.error == null) {
            if (series?.isLast == true) onAction(question.kind.startAction())
            else onAction(LearningAction.NextQuestion(question.id))
        }
    }
    LearningPage(question.kind.trainingTitle(), onBack, backEnabled = !state.busy) {
        learningStatus(state, onAction)
        if (state.needsBudgetPlanning) item { BudgetReminder(true, !state.busy && !state.practiceRetryRequired, onOpenBudget) }
        if (advancing) {
            item { AdventureHeading("Верно!") }
        } else {
            item {
                LearningCard {
                    Text(question.prompt.asGameUiText(), color = GameInk, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.onGloballyPositioned { recordQuestionPart(QuestionExposure.PROMPT, it) })
                }
            }
            if (showExplanation) {
                item { LearningCard(if (question.correct) "Верно!" else "Давай разберёмся") {
                    AdventureBody(question.explanation.asGameUiText())
                } }
                item {
                    if (!question.correct) PracticeButton("Попробовать ещё раз", enabled) { retrying = true }
                    else PracticeButton("Выбрать тему", enabled, onClick = onBack)
                }
            } else {
                item {
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        question.options.forEach { option ->
                            Box(Modifier.fillMaxWidth().onGloballyPositioned {
                                recordQuestionPart(QuestionExposure.optionPart(option.id), it)
                            }) {
                                GameQuizOption(option.text.asGameUiText(), selectedAnswer == option.id, enabled) {
                                    selectedAnswer = option.id
                                }
                            }
                        }
                    }
                }
                item {
                    PracticeButton("Ответить", enabled && selectedAnswer != null) {
                        selectedAnswer?.let { onAction(LearningAction.Answer(it)) }
                    }
                }
            }
        }
    }
}

private fun FinancialQuestionKind.trainingTitle(): String = when (this) {
    FinancialQuestionKind.PLAN_REVIEW -> "План и траты"
    FinancialQuestionKind.CONSEQUENCE -> "Последствия решений"
    FinancialQuestionKind.TRANSACTION_ACCOUNTING -> "Доходы и расходы"
    FinancialQuestionKind.SAVING_PRACTICE -> "Копим на цель"
}

private fun FinancialQuestionKind.trainingDescription(): String = when (this) {
    FinancialQuestionKind.PLAN_REVIEW -> "Планируем и замечаем, что изменилось"
    FinancialQuestionKind.CONSEQUENCE -> "Думаем на шаг вперёд"
    FinancialQuestionKind.TRANSACTION_ACCOUNTING -> "Различаем доход, трату и перевод"
    FinancialQuestionKind.SAVING_PRACTICE -> "Приближаемся к большой покупке"
}

private fun FinancialQuestionKind.trainingArtwork(): Int = when (this) {
    FinancialQuestionKind.PLAN_REVIEW -> R.drawable.budget_needs
    FinancialQuestionKind.CONSEQUENCE -> R.drawable.budget_reserve
    FinancialQuestionKind.TRANSACTION_ACCOUNTING -> R.drawable.menu_coin
    FinancialQuestionKind.SAVING_PRACTICE -> R.drawable.budget_savings
}

private fun FinancialQuestionKind.startAction(): LearningAction = when (this) {
    FinancialQuestionKind.PLAN_REVIEW -> LearningAction.Review
    FinancialQuestionKind.CONSEQUENCE -> LearningAction.ReviewConsequences
    FinancialQuestionKind.TRANSACTION_ACCOUNTING -> LearningAction.ReviewTransactions
    FinancialQuestionKind.SAVING_PRACTICE -> LearningAction.PracticeSaving
}

@Composable
private fun LearningHistoryScreen(state: LearningUiState, onAction: (LearningAction) -> Unit, onBack: () -> Unit) {
    LearningPage("История приключения", onBack) {
        learningStatus(state, onAction)
        if (state.periods.isEmpty() && state.operations.isEmpty() && !state.loading) item {
            LearningCard("Первые страницы впереди") {
                AdventureBody("Здесь сохранятся события приключения, покупки и монеты, которые мы отложим.")
            }
        }
        items(state.periods) { period ->
            var expanded by rememberSaveable(period.title) { mutableStateOf(false) }
            LearningCard(period.title) {
                AdventureBody(period.body.asGameUiText())
                PracticeButton(if (expanded) "Свернуть подробности" else "Посмотреть план и траты", true, primary = false) {
                    expanded = !expanded
                }
                if (expanded) {
                    if (period.plan.isNotEmpty()) AdventureBody(period.plan.asGameUiText())
                    period.actual.forEach { AdventureBody(it.asGameUiText()) }
                    period.note?.let { AdventureBody(it.asGameUiText()) }
                    period.comparisons.forEach { comparison ->
                        HorizontalDivider(color = PracticeBorder)
                        Text(comparison.title.asGameUiText(), color = GameInk, style = MaterialTheme.typography.titleMedium)
                        comparison.rows.forEach { AdventureBody(it.asGameUiText()) }
                        AdventureBody(comparison.note.asGameUiText())
                    }
                }
            }
        }
        if (state.operations.isNotEmpty()) item { AdventureHeading("Последние события") }
        items(state.operations) { operation -> LearningCard { AdventureBody(operation.asGameUiText()) } }
    }
}

@Composable
private fun LearningPage(title: String, onBack: () -> Unit, backEnabled: Boolean = true, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding().background(GamePaper),
        contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp),
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

private fun LazyListScope.learningStatus(state: LearningUiState, onAction: (LearningAction) -> Unit) {
    if (state.loading || state.busy) item {
        LearningCard {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = GameInk, trackColor = PracticeDisabled)
            AdventureBody(if (state.loading) "Открываем страницы приключения…" else "Сохраняем…")
        }
    }
    val message = state.error ?: "Сначала сохраним предыдущий шаг. Повтори попытку, чтобы продолжить."
        .takeIf { state.practiceRetryRequired }
    message?.let { error -> item {
        LearningCard("Не получилось завершить действие") {
            AdventureBody(error.asGameUiText())
            PracticeButton("Повторить", !state.busy) { onAction(LearningAction.Retry) }
        }
    } }
}

@Composable
private fun BudgetReminder(needsPlan: Boolean, enabled: Boolean, onOpenBudget: () -> Unit) {
    LearningCard(if (needsPlan) "Сначала завершим план" else "Теперь уточним план") {
        AdventureBody(if (needsPlan) "Мы ещё не закончили распределять монеты. Подтверди план, чтобы продолжить разбор."
            else "Разницу уже разобрали. Распредели оставшиеся монеты с учётом ближайших нужд и подтверди новый план.")
        PracticeButton(if (needsPlan) "Завершить план" else "Изменить план", enabled, onClick = onOpenBudget)
    }
}

@Composable
private fun LearningCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, contentColor = GameInk,
        shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, PracticeBorder)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it.asGameUiText(), color = GameInk, style = MaterialTheme.typography.titleLarge) }
            content()
        }
    }
}

@Composable
private fun PracticeButton(text: String, enabled: Boolean, primary: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(20.dp), border = if (primary) null else BorderStroke(1.dp, PracticeBorder),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) AdventureLime else Color.White,
            contentColor = GameInk, disabledContainerColor = PracticeDisabled, disabledContentColor = GameInk)) {
        Text(text, color = GameInk, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun LayoutCoordinates.fullyVisibleForPractice(): Boolean {
    if (!isAttached) return false
    val visible = boundsInWindow()
    return visible.width > 0 && visible.height > 0 && visible.width >= size.width - 1 && visible.height >= size.height - 1
}
