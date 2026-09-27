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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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

@Composable
internal fun ChronoscopeScreen(state: LearningUiState, onAction: (LearningAction) -> Unit, onExit: () -> Unit) {
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
            state.quiz?.id?.let { onAction(LearningAction.ChronoscopeQuestionPresented(it)) }
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
            else onAction(LearningAction.ChronoscopeBack)
        },
        backgroundRes = background,
        available = shown?.economy?.availableBalance?.takeIf { showHistoricalBalance },
        savings = shown?.economy?.savingsBalance?.takeIf { showHistoricalBalance },
        sceneFraction = .38f,
        sceneAspectRatio = if (step == ChronoscopeStep.QUIZ) 2.1f else null,
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
            SecondaryAction("Повторить", { onAction(LearningAction.Retry) }, enabled = !state.busy)
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
                                onClick = { onAction(LearningAction.SetReflectionScope(scope)) },
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
                            onAction(LearningAction.SelectMoment(decision.entryId))
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
                        onAction(LearningAction.Simulate(it.decision.entryId, alternative.id))
                    }
                }
            }
            ChronoscopeStep.CONSEQUENCES, ChronoscopeStep.COMPARISON -> {
                GameTitle("Что получилось бы?")
                Horizon(state)
                if (state.simulation?.status == TimeMachineStatus.DIVERGED) {
                    state.simulation.reason?.let { StoryNote("Дальше пути расходятся", it) }
                }
                PathsComparison(state)
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
                SecondaryAction("Ещё раз сравнить пути", { onAction(LearningAction.ComparePaths) }, enabled = !state.busy)
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
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled,
        shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, GameInk.copy(alpha = .35f)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk, containerColor = Color.White,
            disabledContainerColor = GameDisabledButtonContainer, disabledContentColor = GameDisabledButtonContent)) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuizScene(state: LearningUiState) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFFDAD9E9), Color(0xFFF5F1E7)),
    )), contentAlignment = Alignment.BottomCenter) {
        val pet = state.memory?.before?.pet ?: state.realGame?.pet
        pet?.let { adventurePetArtwork(it)?.let { art ->
            MovingPetArtwork(art, pet.name, pet.toAdventurePetPresentation().motionIntensity,
                Modifier.fillMaxHeight().aspectRatio(1f))
        } }
    }
}

@Composable
private fun QuizExplanation(state: LearningUiState, onAction: (LearningAction) -> Unit, fallback: String) {
    val answer = state.quizAnswer
    AdventureBody((answer?.explanation ?: fallback).asGameUiText(), Modifier.reportFullyVisible {
        answer?.submissionId?.let { onAction(LearningAction.ChronoscopeExplanationPresented(answer.quizId, it)) }
    })
}

private fun Modifier.reportFullyVisible(onVisible: () -> Unit): Modifier = onGloballyPositioned { coordinates ->
    if (coordinates.isFullyVisibleInWindow()) onVisible()
}

private fun LayoutCoordinates.isFullyVisibleInWindow(): Boolean {
    if (!isAttached) return false
    val bounds = boundsInWindow()
    return bounds.width > 0 && bounds.height > 0 && bounds.width >= size.width - 1 && bounds.height >= size.height - 1
}

@Composable
private fun ChronoscopeActions(state: LearningUiState, selectedOptionId: String?, onAction: (LearningAction) -> Unit, onExit: () -> Unit) {
    val step = state.chronoscopeStep ?: return
    if (step == ChronoscopeStep.QUIZ) {
        val quiz = state.quiz
        val selected = selectedOptionId?.takeIf { id -> quiz?.options?.any { it.id == id } == true }
        AdventurePrimaryButton("Ответить", {
            if (quiz != null && selected != null) onAction(LearningAction.AnswerQuiz(quiz.id, selected))
        }, enabled = !state.busy && selected != null)
        SecondaryAction("Посмотреть сравнение", { onAction(LearningAction.ComparePaths) }, enabled = !state.busy)
        SecondaryAction("К приключению", onExit, enabled = !state.busy)
        return
    }
    val primary = when (step) {
        ChronoscopeStep.COMPARISON, ChronoscopeStep.CONSEQUENCES -> when {
            state.quizAnswer?.correct == true -> "Почему так получилось" to LearningAction.StartQuiz
            state.quiz != null -> "Почему так получилось?" to LearningAction.StartQuiz
            else -> null
        }
        ChronoscopeStep.RETRY -> "Попробовать ещё раз" to LearningAction.RetryQuiz
        ChronoscopeStep.UNAVAILABLE -> "Выбрать другой момент" to LearningAction.ShowMoments
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
            { onAction(LearningAction.ShowMoments) }, enabled = !state.busy)
        else -> Unit
    }
}

@Composable
private fun Horizon(state: LearningUiState) {
    state.simulation?.let { GameBody(chronoscopeHorizon(it, state.memory?.decision?.day)) }
    state.comparisonBoundary?.let {
        Text(it, color = GameInk.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PathsComparison(state: LearningUiState) {
    val original = state.originalPath ?: return
    val alternative = state.alternativePath ?: return
    val differences = chronoscopeDifferences(original, alternative,
        showLedgerTotal = state.quiz?.kind == TimeMachineQuizKind.LEDGER)
    if (differences.original.isNotEmpty()) PathConsequences("В нашей истории", differences.original, Color(0xFFE4EFF8))
    if (differences.alternative.isNotEmpty()) PathConsequences("При другом выборе", differences.alternative, Color(0xFFECF4D7))
    if (differences.money.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = Color.White,
        border = BorderStroke(1.dp, GameInk.copy(alpha = .10f))) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(14.dp)) {
            val stacked = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.4f
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!stacked) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1.25f))
                    PathLabel("Было", Color(0xFFE4EFF8), Modifier.weight(1f))
                    PathLabel("Могло быть", Color(0xFFECF4D7), Modifier.weight(1f))
                }
                differences.money.forEach { row -> ComparisonAmount(row.label, row.original, row.alternative, stacked) }
            }
        }
    }
}

@Composable
private fun ComparisonAmount(label: String, original: Long, alternative: Long, stacked: Boolean) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = GameInk, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PathLabel("Было\n$original", Color(0xFFE4EFF8), Modifier.weight(1f))
                PathLabel("Могло быть\n$alternative", Color(0xFFECF4D7), Modifier.weight(1f))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1.25f), color = GameInk, style = MaterialTheme.typography.bodyMedium)
            Text(original.toString(), Modifier.weight(1f), color = GameInk,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(alternative.toString(), Modifier.weight(1f), color = GameInk,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PathLabel(text: String, color: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = color) {
        Text(text, Modifier.padding(horizontal = 5.dp, vertical = 7.dp), color = GameInk,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PathConsequences(title: String, consequences: List<String>, color: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = color) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = GameInk, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            consequences.forEach { GameBody(it) }
        }
    }
}

@Composable
private fun ComparisonScene(state: LearningUiState) {
    // Both outcomes share one calm stage. The full transparent character canvases
    // keep the same movement and ground-contact treatment as the rest of the game.
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(Color(0xFFDAD9E9), Color(0xFFF5F1E7)),
    ))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple("Было", state.originalPath, Color(0xFFE8EFF5)),
                Triple("Могло быть", state.alternativePath, Color(0xFFF0F2E4)),
            ).forEach { (label, path, tint) ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                    .background(tint.copy(alpha = .7f)).padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(horizontal = 8.dp), color = GameInk,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        path?.state?.pet?.let { pet -> adventurePetArtwork(pet)?.let { art ->
                            MovingPetArtwork(art, pet.name, pet.toAdventurePetPresentation().motionIntensity,
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
