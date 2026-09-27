package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GoalCream = Color(0xfffbfaef)
internal val GoalInk = Color(0xff231942)
internal val GoalPurple = Color(0xff6540bd)
private val GoalLime = Color(0xffa8e830)

/** Presentation data only; the caller owns selection and game commands. */
@Immutable
data class AdventureGoalOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val introduction: String,
    val image: Int,
    val itemImage: Int? = null,
)

@Composable
fun AdventureGoalBriefingScreen(
    artwork: CustomizationArtwork,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    saving: Boolean = false,
    saveFailed: Boolean = false,
    chapterPreviewRes: Int? = null,
) {
    AdventureGoalLayout(artwork, chapterPreviewRes ?: artwork.background, "Задание Смотрителей",
        "Назад к аксессуарам", onBack, largeStage = true, saving = saving, stage = {}) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalHeading("Впереди большое приключение", artwork)
            Text("Ночь наблюдений", color = GoalPurple, fontFamily = artwork.titleFont,
                fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
            Text("Это наше первое задание от Гильдии Смотрителей. " +
                "Поможем подготовиться к наблюдениям за звёздами!",
                color = GoalInk, fontFamily = artwork.bodyFont, fontSize = 16.sp, lineHeight = 23.sp)
            Text("Нужны карта звёзд, штатив, телескоп и поездка. Выберем, с чего начать.",
                color = GoalInk.copy(alpha = .8f), fontFamily = artwork.bodyFont,
                fontSize = 15.sp, lineHeight = 22.sp)
        }
        if (saveFailed) GoalSaveError(artwork)
        GoalFooter("Дальше", artwork, !saving, onContinue)
    }
}

@Composable
fun AdventureGoalSelectionScreen(
    artwork: CustomizationArtwork,
    goals: List<AdventureGoalOption>,
    selectedGoalId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    AdventureGoalLayout(artwork, artwork.background, "Выбери цель накопления",
        "Назад к началу приключения", onBack, saving = saving, stage = {}) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalHeading("На что будем копить сначала?", artwork)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                goals.forEach { goal ->
                    val selected = goal.id == selectedGoalId
                    val shape = RoundedCornerShape(20.dp)
                    Row(Modifier.fillMaxWidth().clip(shape)
                        .background(if (selected) Color(0xffefebfb) else Color.White.copy(alpha = .75f))
                        .border(if (selected) 2.dp else 1.dp,
                            if (selected) GoalPurple else Color(0xffe1deeb), shape)
                        .selectable(selected, enabled = !saving, role = Role.RadioButton, onClick = { onSelect(goal.id) })
                        .padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        goal.itemImage?.let { resource ->
                            Image(painterResource(resource), null,
                                Modifier.size(64.dp), contentScale = ContentScale.Fit)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(goal.title, fontFamily = artwork.titleFont, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, color = GoalInk)
                            GoalBody(goal.subtitle, artwork)
                        }
                        RadioButton(selected, onClick = null, modifier = Modifier.size(24.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = GoalPurple,
                                unselectedColor = Color(0xffb0a1ca)))
                    }
                }
            }
            GoalBody("Это части одного задания. Выбор можно изменить позже — накопленные монеты сохранятся.", artwork)
        }
        if (saveFailed) GoalSaveError(artwork)
        GoalFooter(if (saving) "Сохраняем…" else "Начать с этого", artwork,
            !saving && goals.any { it.id == selectedGoalId }, onConfirm)
    }
}

@Composable
fun AdventureStartedScreen(
    artwork: CustomizationArtwork,
    goal: AdventureGoalOption,
    portrait: Int,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    AdventureGoalLayout(artwork, goal.image, "Новое приключение", "Назад к выбору цели", onBack,
        largeStage = true, saving = saving,
        stage = {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Image(painterResource(portrait), "Твой спутник готов к приключению",
                    Modifier.size(minOf(maxWidth * .82f, maxHeight, 380.dp)), contentScale = ContentScale.Fit)
            }
        }) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("НОВОЕ ПРИКЛЮЧЕНИЕ НАЧАЛОСЬ", color = GoalPurple, fontFamily = artwork.bodyFont,
                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp)
            GoalHeading(goal.title, artwork, large = true)
            GoalBody(goal.introduction, artwork)
        }
        if (saveFailed) GoalSaveError(artwork)
        GoalFooter(if (saving) "Сохраняем…" else "В путь!", artwork, !saving, onContinue)
    }
}

/** Shared header is identical to customization/accessories. Wide windows place art beside content. */
@Composable
internal fun AdventureGoalLayout(
    artwork: CustomizationArtwork,
    background: Int,
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    largeStage: Boolean = false,
    saving: Boolean = false,
    stage: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(GoalCream)) {
        val wide = maxWidth >= 720.dp
        val scene: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                Image(painterResource(background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Column(Modifier.fillMaxSize()) {
                    CustomizationHeader(onBack, artwork, saving = saving, title = title, backDescription = backDescription)
                    Box(Modifier.weight(1f).fillMaxWidth()) { stage() }
                    if (!wide) Spacer(Modifier.height(24.dp))
                }
            }
        }
        val panel: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier.background(GoalCream,
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(
                    if (wide) WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
                    else WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxHeight()) { content() }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                scene(Modifier.weight(1f).fillMaxHeight())
                panel(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            val sceneHeight = if (largeStage) (maxHeight * .55f).coerceAtMost(520.dp)
                else (maxHeight * .24f).coerceIn(100.dp, 240.dp)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy((-24).dp)) {
                scene(Modifier.fillMaxWidth().height(sceneHeight))
                panel(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
internal fun GoalHeading(text: String, artwork: CustomizationArtwork, large: Boolean = false) {
    Text(text, color = GoalInk, fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold,
        fontSize = if (large) 32.sp else 23.sp, lineHeight = if (large) 37.sp else 29.sp)
}

@Composable
private fun GoalBody(text: String, artwork: CustomizationArtwork) {
    Text(text, color = GoalInk.copy(alpha = .8f), fontFamily = artwork.bodyFont,
        fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
internal fun GoalFooter(text: String, artwork: CustomizationArtwork, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp).heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 3.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GoalLime, contentColor = GoalInk)) {
        Text(text, fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

@Composable
private fun GoalSaveError(artwork: CustomizationArtwork) {
    Text("Не удалось сохранить выбор. Попробуй ещё раз.",
        Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.error,
        fontFamily = artwork.bodyFont, fontSize = 14.sp)
}
