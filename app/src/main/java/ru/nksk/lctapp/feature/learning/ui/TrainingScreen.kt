package ru.nksk.lctapp.feature.learning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectableGroup
import ru.nksk.lctapp.core.ui.components.GameQuizOption
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.domain.finance.FinancialQuestion
import ru.nksk.lctapp.domain.finance.FinancialQuestionKind

@Composable
internal fun TrainingScreen(state: TrainingUiState, onAction: (TrainingAction) -> Unit, onBack: () -> Unit,
    onOpenBudget: () -> Unit = {}, onContinueStory: () -> Unit = onBack) {
    val question = state.question.takeIf { state.practiceOpen }
    val closeQuestion = {
        if (!state.busy) {
            if (state.needsBudgetPlanning || state.practiceRetryRequired) onBack()
            else onAction(TrainingAction.CloseQuestion)
        }
    }
    BackHandler(enabled = question != null) { closeQuestion() }
    if (question != null) {
        PracticeQuestionScreen(state, question, onAction, closeQuestion, onOpenBudget, onContinueStory)
    } else if (state.chapterPractice) {
        LearningPage("Перед продолжением истории", onBack) {
            learningStatus(state.loading, state.busy, state.error, state.practiceRetryRequired, showSaving = false) {
                onAction(TrainingAction.Retry)
            }
            if (!state.loading && state.hasGame) item {
                if (state.needsBudgetPlanning) BudgetReminder(true, !state.busy, onOpenBudget)
                else ChapterPracticeNext(state, onAction, onOpenBudget, onContinueStory)
            }
        }
    } else LearningPage("Тренировка навыков", onBack) {
        learningStatus(state.loading, state.busy, state.error, state.practiceRetryRequired) { onAction(TrainingAction.Retry) }
        if (!state.loading && state.hasGame) {
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
private fun PracticeQuestionScreen(state: TrainingUiState, question: FinancialQuestion,
    onAction: (TrainingAction) -> Unit, onBack: () -> Unit, onOpenBudget: () -> Unit,
    onContinueStory: () -> Unit) {
    var retrying by rememberSaveable(question.id, question.attempts) { mutableStateOf(false) }
    val series = question.series
    val advancing = !state.chapterPractice && question.correct && series != null
    var selectedAnswer by rememberSaveable(question.id, question.attempts) { mutableStateOf<String?>(null) }
    val showExplanation = question.answeredOptionId != null && !retrying && !advancing
    val enabled = !state.needsBudgetPlanning && !state.practiceRetryRequired
    val interactionBlocked = state.busy || advancing
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
            onAction(TrainingAction.QuestionPresented(question.id))
        }
    }
    LaunchedEffect(showingQuestion, lifecycleState, questionExposure) {
        if (showingQuestion && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            questionCoordinates.toList().forEach { (part, coordinates) -> recordQuestionPart(part, coordinates) }
        }
    }
    val automaticAdvance = state.automaticAdvance(lifecycleState.isAtLeast(Lifecycle.State.RESUMED))
    LaunchedEffect(automaticAdvance) { automaticAdvance?.let(onAction) }
    // Only a new question gets a fresh scroll position; saving an answer keeps the same page footprint.
    val questionScroll = key(question.id) { rememberLazyListState() }
    LearningPage(question.kind.trainingTitle(), onBack, backEnabled = !state.busy, listState = questionScroll) {
        learningStatus(state.loading, state.busy, state.error, state.practiceRetryRequired, showSaving = false) { onAction(TrainingAction.Retry) }
        if (state.needsBudgetPlanning) item { BudgetReminder(true, !state.busy && !state.practiceRetryRequired, onOpenBudget) }
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
                if (!question.correct) PracticeButton("Попробовать ещё раз", enabled,
                    interactionBlocked = interactionBlocked) { retrying = true }
                else if (state.chapterPractice) ChapterPracticeNext(state, onAction, onOpenBudget, onContinueStory)
                else PracticeButton("Выбрать тему", enabled, interactionBlocked = interactionBlocked, onClick = onBack)
            }
        } else {
            item {
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    question.options.forEach { option ->
                        Box(Modifier.fillMaxWidth().onGloballyPositioned {
                            recordQuestionPart(QuestionExposure.optionPart(option.id), it)
                        }) {
                            GameQuizOption(option.text.asGameUiText(),
                                (selectedAnswer ?: question.answeredOptionId.takeIf { advancing }) == option.id,
                                enabled, interactionBlocked = interactionBlocked) {
                                selectedAnswer = option.id
                            }
                        }
                    }
                }
            }
            item {
                PracticeButton("Ответить", enabled && (selectedAnswer != null || advancing),
                    interactionBlocked = interactionBlocked) {
                    selectedAnswer?.let { onAction(TrainingAction.Answer(it)) }
                }
            }
        }
    }
}

@Composable
private fun ChapterPracticeNext(state: TrainingUiState, onAction: (TrainingAction) -> Unit,
    onOpenBudget: () -> Unit, onContinueStory: () -> Unit) {
    val enabled = !state.needsBudgetPlanning && !state.practiceRetryRequired && state.error == null
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state.chapterStep) {
            ChapterPracticeStep.SAVING, ChapterPracticeStep.REVIEW -> {
                AdventureBody(if (state.chapterStep == ChapterPracticeStep.SAVING)
                    "Вспомним, как мы копили на снаряжение. Ответим на вопрос и продолжим разбор."
                else "Посмотрим, что планировали и сколько потратили. После разбора вернёмся к истории.")
                PracticeButton(if (state.question?.correct == true) "Следующий вопрос" else "Начать разбор",
                    enabled, interactionBlocked = state.busy) { onAction(TrainingAction.StartChapterPractice) }
            }
            ChapterPracticeStep.BUDGET -> BudgetReminder(false, enabled && !state.busy, onOpenBudget)
            ChapterPracticeStep.FOOD -> {
                AdventureBody("С вопросами закончили. Осталось позаботиться о еде для спутника.")
                PracticeButton("К приключению", enabled, interactionBlocked = state.busy, onClick = onContinueStory)
            }
            ChapterPracticeStep.COMPLETE -> {
                AdventureBody("С разбором закончили. Всё готово, чтобы продолжить историю!")
                PracticeButton("Продолжить историю", enabled, interactionBlocked = state.busy, onClick = onContinueStory)
            }
            null -> Unit
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

private fun FinancialQuestionKind.startAction(): TrainingAction = when (this) {
    FinancialQuestionKind.PLAN_REVIEW -> TrainingAction.Review
    FinancialQuestionKind.CONSEQUENCE -> TrainingAction.ReviewConsequences
    FinancialQuestionKind.TRANSACTION_ACCOUNTING -> TrainingAction.ReviewTransactions
    FinancialQuestionKind.SAVING_PRACTICE -> TrainingAction.PracticeSaving
}

private fun LayoutCoordinates.fullyVisibleForPractice(): Boolean {
    if (!isAttached) return false
    val visible = boundsInWindow()
    return visible.width > 0 && visible.height > 0 && visible.width >= size.width - 1 && visible.height >= size.height - 1
}
