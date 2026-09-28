package ru.nksk.lctapp.feature.learning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.game.adventurePetArtwork
import ru.nksk.lctapp.core.ui.game.asGameActionLabel
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.core.ui.location.locationArtwork
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.domain.pet.renderPetText
import ru.nksk.lctapp.domain.timemachine.TimeMachineQuizKind
import ru.nksk.lctapp.domain.timemachine.TimeMachineStatus

private val PathRule = Color(0xFFE5DCC6)

@Composable
internal fun ReflectionScreen(state: ReflectionUiState, onAction: (ReflectionAction) -> Unit, onBack: () -> Unit) {
    if (state.chronoscopeStep != null) ChronoscopeScreen(state, onAction, onBack)
    else LearningPage("А что, если…", onBack) {
        learningStatus(state.loading, state.busy, state.error, onRetry = { onAction(ReflectionAction.Retry) })
    }
}

@Composable
internal fun ChronoscopeScreen(state: ReflectionUiState, onAction: (ReflectionAction) -> Unit, onExit: () -> Unit) {
    val step = state.chronoscopeStep ?: return
    val memory = state.memory
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val questionExposure = remember(state.quiz?.id, state.quiz?.prompt, state.quiz?.options) {
        state.quiz?.let { QuestionExposure(it.options.map { option -> option.id }) }
    }
    val questionCoordinates = remember(questionExposure) { mutableMapOf<String, LayoutCoordinates>() }
    fun recordQuestionPart(part: String, coordinates: LayoutCoordinates) {
        questionCoordinates[part] = coordinates
        if (step == ChronoscopeStep.QUIZ && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            questionExposure?.record(part, coordinates.isFullyVisibleInWindow()) == true) {
            state.quiz?.id?.let { onAction(ReflectionAction.ChronoscopeQuestionPresented(it)) }
        }
    }
    LaunchedEffect(step, lifecycleState, questionExposure) {
        if (step == ChronoscopeStep.QUIZ && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            questionCoordinates.toList().forEach { (part, coordinates) -> recordQuestionPart(part, coordinates) }
        }
    }
    var selectedOptionId by rememberSaveable(state.quiz?.id, step, state.quizAnswer?.attempt) {
        mutableStateOf<String?>(null)
    }
    val contentScroll = key(step, state.quiz?.id) { rememberScrollState() }
    val shown = when (step) {
        ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS, ChronoscopeStep.PRESENT -> state.realGame
        ChronoscopeStep.CONSEQUENCES -> state.alternativePath?.state
        else -> memory?.before ?: state.realGame
    }
    val name = shown?.pet?.name.orEmpty()
    val title = when (step) {
        ChronoscopeStep.MEMORY, ChronoscopeStep.ALTERNATIVES -> "День ${memory?.decision?.day ?: 1}"
        ChronoscopeStep.COMPARISON, ChronoscopeStep.CONSEQUENCES -> "Два пути"
        ChronoscopeStep.QUIZ, ChronoscopeStep.RETRY -> "Почему так?"
        ChronoscopeStep.EXPLANATION -> "Вот что изменилось"
        else -> "А что, если…"
    }
    val showHistoricalBalance = step in setOf(ChronoscopeStep.MEMORY, ChronoscopeStep.ALTERNATIVES)
    val background = memory?.sceneKey?.takeUnless {
        step in setOf(ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS, ChronoscopeStep.PRESENT)
    }?.let(::gameScene) ?: shown?.let { locationArtwork(it.locationScene) } ?: R.drawable.location_observatory_stage
    val stagedScene: (@Composable BoxScope.() -> Unit)? = when (step) {
        ChronoscopeStep.COMPARISON, ChronoscopeStep.CONSEQUENCES -> {{ ComparisonScene(state) }}
        ChronoscopeStep.QUIZ -> {{ QuizScene(state) }}
        else -> null
    }
    AdventureScreen(
        title = title,
        onBack = {
            if (step in setOf(ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS, ChronoscopeStep.PRESENT)) onExit()
            else onAction(ReflectionAction.ChronoscopeBack)
        },
        backgroundRes = background,
        available = shown?.economy?.availableBalance?.takeIf { showHistoricalBalance },
        savings = shown?.economy?.savingsBalance?.takeIf { showHistoricalBalance },
        sceneFraction = .38f,
        sceneAspectRatio = if (step == ChronoscopeStep.QUIZ) 2.1f else null,
        sceneBottomColor = if (step == ChronoscopeStep.QUIZ) GamePaper else Color.Transparent,
        scene = stagedScene,
        contentScrollState = contentScroll,
        footer = { ChronoscopeActions(state, selectedOptionId, onAction, onExit) },
        characterRes = shown?.pet?.let(::adventurePetArtwork),
        characterMotionIntensity = shown?.pet?.toAdventurePetPresentation()?.motionIntensity ?: 1f,
        characterDescription = shown?.pet?.name,
        speech = when (step) {
            ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS -> "Давай посмотрим, куда привёл бы другой выбор!"
            ChronoscopeStep.MEMORY, ChronoscopeStep.ALTERNATIVES -> "А что, если поступить иначе?"
            ChronoscopeStep.QUIZ -> "Как думаешь, почему получилось именно так?"
            ChronoscopeStep.RETRY -> "Давай разберёмся и попробуем ещё раз."
            ChronoscopeStep.EXPLANATION -> "Один выбор может изменить дальнейший путь."
            else -> null
        },
    ) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AdventureLime)
        state.error?.let {
            GameBody(it)
            SecondaryAction("Повторить", { onAction(ReflectionAction.Retry) }, enabled = !state.busy)
        }
        when (step) {
            ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS -> {
                GameTitle("Какой момент вспомним?")
                GameBody("Попробуем другой выбор и сравним, что получилось бы.")
                state.reflectionDay?.let {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ReflectionScope.DAY to "Этот день", ReflectionScope.WEEK to "Эта неделя").forEach { (scope, label) ->
                            FilterChip(
                                selected = state.reflectionScope == scope,
                                onClick = { onAction(ReflectionAction.SetReflectionScope(scope)) },
                                enabled = !state.busy,
                                label = { Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(labelColor = GameInk,
                                    selectedContainerColor = GameInk, selectedLabelColor = Color.White),
                            )
                        }
                    }
                }
                val moments = state.timeMachine?.decisions.orEmpty().asReversed()
                moments.groupBy { it.day }.forEach { (day, decisions) ->
                    Text(day?.let { "День $it" } ?: "Из приключения", color = GameInk.copy(alpha = .65f),
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    decisions.forEach { decision ->
                        ChoiceAction(renderPetText(decision.title, name).asGameUiText(), !state.busy) {
                            onAction(ReflectionAction.SelectMoment(decision.entryId))
                        }
                    }
                }
                if (moments.isEmpty()) {
                    StoryNote("Пока нечего сравнить", state.timeMachine?.reason
                        ?: "Здесь появятся твои решения, у которых был другой доступный вариант.")
                }
            }
            ChronoscopeStep.MEMORY, ChronoscopeStep.ALTERNATIVES -> memory?.let {
                GameTitle(renderPetText(it.decision.title, name).asGameUiText())
                StoryNote("Тогда мы решили", it.originalAction)
                Text("Монеты сверху — перед этим решением", color = GameInk.copy(alpha = .65f),
                    style = MaterialTheme.typography.labelMedium)
                it.knownNeeds?.takeIf { needed -> needed > 0 }?.let { needed ->
                    GameBody("На ближайшие нужды тогда требовалось $needed монет.")
                }
                Text("А если выбрать так?", color = GameInk, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                it.decision.alternatives.forEach { alternative ->
                    ChoiceAction(renderPetText(alternative.title, name).asGameActionLabel(), !state.busy) {
                        onAction(ReflectionAction.Simulate(it.decision.entryId, alternative.id))
                    }
                }
            }
            ChronoscopeStep.CONSEQUENCES, ChronoscopeStep.COMPARISON -> {
                GameTitle(memory?.decision?.title?.let { renderPetText(it, name).asGameUiText() } ?: "А если поступить иначе?")
                Horizon(state)
                PathsComparison(state)
                if (state.simulation?.status == TimeMachineStatus.DIVERGED) {
                    StoryNote("Что дальше?", "Дальше этот вариант пока не можем показать. " +
                        "Здесь только те события, которые получилось сравнить.")
                }
            }
            ChronoscopeStep.QUIZ -> state.quiz?.let { quiz ->
                AdventureHeading(quiz.prompt.asGameUiText(), Modifier.onGloballyPositioned {
                    recordQuestionPart(QuestionExposure.PROMPT, it)
                })
                Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quiz.options.forEach { option ->
                        Box(Modifier.fillMaxWidth().onGloballyPositioned {
                            recordQuestionPart(QuestionExposure.optionPart(option.id), it)
                        }) {
                            GameQuizOption(option.text.asGameUiText(), selectedOptionId == option.id, !state.busy) {
                                selectedOptionId = option.id
                            }
                        }
                    }
                }
            }
            ChronoscopeStep.RETRY -> {
                GameTitle("Посмотрим внимательнее")
                QuizExplanation(state, onAction, "Сравни, какие действия изменились в двух путях.")
                SecondaryAction("Ещё раз сравнить пути", { onAction(ReflectionAction.ComparePaths) }, enabled = !state.busy)
            }
            ChronoscopeStep.EXPLANATION -> {
                GameTitle(if (state.quizAnswer?.correct == true) "Верно!" else "У каждого выбора свои последствия")
                QuizExplanation(state, onAction, "Сравнивай не только монеты: другой поступок может сохранить силы или помочь получить нужную вещь.")
            }
            ChronoscopeStep.PRESENT -> {
                GameTitle("Вернёмся к приключению")
                GameBody("Мы посмотрели другой путь. Твоя история продолжается.")
            }
            ChronoscopeStep.UNAVAILABLE -> {
                GameTitle("Этот путь не удалось сравнить")
                GameBody(state.simulation?.reason ?: "В воспоминании не сохранилось всего, что нужно для сравнения.")
                GameBody("Попробуем другой момент.")
            }
        }
    }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    GameActionButton(text, onClick, enabled = enabled, style = GameActionStyle.SECONDARY,
        minHeight = 52.dp, shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        textStyle = MaterialTheme.typography.labelLarge)
}

@Composable
private fun QuizScene(state: ReflectionUiState) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFFDAD9E9), GamePaper),
    )), contentAlignment = Alignment.BottomCenter) {
        val pet = state.memory?.before?.pet ?: state.realGame?.pet
        pet?.let { adventurePetArtwork(it)?.let { art ->
            MovingPetArtwork(art, pet.name, pet.toAdventurePetPresentation().motionIntensity,
                Modifier.fillMaxHeight().aspectRatio(1f))
        } }
    }
}

@Composable
private fun QuizExplanation(state: ReflectionUiState, onAction: (ReflectionAction) -> Unit, fallback: String) {
    val answer = state.quizAnswer
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    var coordinates by remember(answer?.quizId, answer?.submissionId) { mutableStateOf<LayoutCoordinates?>(null) }
    fun recordExplanation() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            coordinates?.isFullyVisibleInWindow() == true) {
            answer?.submissionId?.let { onAction(ReflectionAction.ChronoscopeExplanationPresented(answer.quizId, it)) }
        }
    }
    LaunchedEffect(lifecycleState, answer?.quizId, answer?.submissionId) { recordExplanation() }
    AdventureBody((answer?.explanation ?: fallback).asGameUiText(), Modifier.onGloballyPositioned {
        coordinates = it
        recordExplanation()
    })
}

private fun LayoutCoordinates.isFullyVisibleInWindow(): Boolean {
    if (!isAttached) return false
    val bounds = boundsInWindow()
    return bounds.width > 0 && bounds.height > 0 && bounds.width >= size.width - 1 && bounds.height >= size.height - 1
}

@Composable
private fun ChronoscopeActions(state: ReflectionUiState, selectedOptionId: String?, onAction: (ReflectionAction) -> Unit, onExit: () -> Unit) {
    val step = state.chronoscopeStep ?: return
    if (step == ChronoscopeStep.QUIZ) {
        val quiz = state.quiz
        val selected = selectedOptionId?.takeIf { id -> quiz?.options?.any { it.id == id } == true }
        AdventurePrimaryButton("Ответить", {
            if (quiz != null && selected != null) onAction(ReflectionAction.AnswerQuiz(quiz.id, selected))
        }, enabled = !state.busy && selected != null)
        SecondaryAction("Посмотреть сравнение", { onAction(ReflectionAction.ComparePaths) }, enabled = !state.busy)
        SecondaryAction("К приключению", onExit, enabled = !state.busy)
        return
    }
    val primary = when (step) {
        ChronoscopeStep.COMPARISON, ChronoscopeStep.CONSEQUENCES -> when {
            state.quizAnswer?.correct == true -> "Почему так получилось" to ReflectionAction.StartQuiz
            state.quiz != null -> "Почему так получилось?" to ReflectionAction.StartQuiz
            else -> null
        }
        ChronoscopeStep.RETRY -> "Попробовать ещё раз" to ReflectionAction.RetryQuiz
        ChronoscopeStep.UNAVAILABLE -> "Выбрать другой момент" to ReflectionAction.ShowMoments
        else -> null
    }
    primary?.let { (text, action) ->
        AdventurePrimaryButton(text, { onAction(action) }, enabled = !state.busy)
    }
    if (step in setOf(ChronoscopeStep.EXPLANATION, ChronoscopeStep.PRESENT) ||
        step in setOf(ChronoscopeStep.COMPARISON, ChronoscopeStep.CONSEQUENCES) && primary == null ||
        step == ChronoscopeStep.INTRO && state.timeMachine?.decisions.isNullOrEmpty()) {
        AdventurePrimaryButton("К приключению", onExit, enabled = !state.busy)
    } else {
        SecondaryAction("К приключению", onExit, enabled = !state.busy)
    }
    when (step) {
        ChronoscopeStep.EXPLANATION -> SecondaryAction("Вспомнить другой момент",
            { onAction(ReflectionAction.ShowMoments) }, enabled = !state.busy)
        else -> Unit
    }
}

@Composable
private fun Horizon(state: ReflectionUiState) {
    state.simulation?.let { GameBody(chronoscopeHorizon(it, state.memory?.decision?.day)) }
    state.comparisonBoundary?.takeIf { state.simulation?.status == TimeMachineStatus.DIVERGED }?.let {
        Text(it, color = GameInk.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PathsComparison(state: ReflectionUiState) {
    val original = state.originalPath ?: return
    val alternative = state.alternativePath ?: return
    val differences = chronoscopeDifferences(original, alternative,
        showLedgerTotal = state.quiz?.kind == TimeMachineQuizKind.LEDGER)
    val selectedAlternative = state.memory?.decision?.alternatives?.find {
        it.id == state.simulation?.request?.alternativeId
    }
    PathConsequences("Как было", "Выбрали «${state.memory?.originalChoice ?: "Прежний поступок"}»",
        differences.original + chronoscopeMoneyLines(differences, alternative = false))
    val otherChoice = selectedAlternative?.title?.let {
        renderPetText(it, original.state.pet.name).asGameActionLabel()
    } ?: "Другой поступок"
    PathConsequences("А если выбрать", "«$otherChoice»",
        differences.alternative + chronoscopeMoneyLines(differences, alternative = true))
}

@Composable
private fun PathConsequences(title: String, choice: String, consequences: List<String>) {
    Surface(shape = RoundedCornerShape(18.dp), color = GamePaper, border = BorderStroke(1.dp, PathRule)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = GameInk.copy(alpha = .72f), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold)
            Text(choice, color = GameInk, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            if (consequences.isNotEmpty()) HorizontalDivider(color = PathRule)
            consequences.forEach { Text(it, color = GameInk, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun ComparisonScene(state: ReflectionUiState) {
    // Keep the adventure scene visible; only each pet has a quiet paper backdrop.
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Было" to state.originalPath, "Могло быть" to state.alternativePath).forEach { (label, path) ->
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(24.dp),
                color = GamePaper, border = BorderStroke(1.dp, PathRule)) {
                Column(Modifier.fillMaxSize().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(horizontal = 8.dp), color = GameInk,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        path?.petPresentation?.let { pet -> pet.artworkRes?.let { art ->
                            MovingPetArtwork(art, pet.name, pet.motionIntensity,
                                Modifier.fillMaxSize())
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryNote(title: String, body: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = GameInk.copy(alpha = .045f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = GameInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            GameBody(body)
        }
    }
}

@Composable
private fun ChoiceAction(text: String, enabled: Boolean, showArrow: Boolean = true, action: () -> Unit) {
    OutlinedButton(action, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GameInk.copy(alpha = .20f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk, containerColor = Color.White)) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (showArrow) Icon(painterResource(R.drawable.menu_chevron), null,
            Modifier.padding(start = 12.dp).size(18.dp), tint = GameInk)
    }
}
