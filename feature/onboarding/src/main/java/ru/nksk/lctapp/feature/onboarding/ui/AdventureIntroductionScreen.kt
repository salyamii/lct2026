package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val IntroCream = Color(0xfffbfaef)
private val IntroInk = Color(0xff231942)
private val IntroMuted = Color(0xff79738b)

/** Final onboarding explanation. State and navigation are owned by the app entry. */
@Composable
fun AdventureIntroductionScreen(
    artwork: CustomizationArtwork,
    portrait: Int,
    icons: Map<String, Int>,
    onBack: () -> Unit,
    onStart: () -> Unit,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    MaterialTheme(colorScheme = lightColorScheme(), typography = Typography(
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = artwork.bodyFont),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = artwork.bodyFont),
    )) {
        BoxWithConstraints(Modifier.fillMaxSize().background(IntroCream)) {
            if (needsOnboardingScroll(maxHeight)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth()) {
                        Image(painterResource(artwork.background), null, Modifier.matchParentSize()
                            .testTag("onboarding_scene_background"), contentScale = ContentScale.Crop)
                        Column {
                            CustomizationHeader(onBack, artwork, saving = saving, title = "Всё готово!", backDescription = "Назад к аксессуарам")
                            Image(painterResource(portrait), "Твой спутник готов к приключениям",
                                Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit)
                        }
                    }
                    IntroductionContent(artwork, Modifier.fillMaxWidth().windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                        icons = icons, onStart = onStart, saving = saving, saveFailed = saveFailed, scrollAll = true)
                }
            } else if (maxWidth >= 720.dp) {
                Row(Modifier.fillMaxSize()) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        Image(painterResource(artwork.background), null, Modifier.matchParentSize().testTag("onboarding_scene_background"), contentScale = ContentScale.Crop)
                        Image(painterResource(portrait), "Твой спутник готов к приключениям",
                            Modifier.size(minOf(maxWidth * .95f, maxHeight * .75f, 520.dp)).align(Alignment.Center))
                        CustomizationHeader(onBack, artwork, saving = saving, title = "Всё готово!", backDescription = "Назад к аксессуарам")
                    }
                    IntroductionContent(artwork, Modifier.weight(1f).fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)), centerContent = true, icons = icons, onStart = onStart, saving = saving, saveFailed = saveFailed)
                }
            } else {
                val stageHeight = (maxHeight * .27f).coerceIn(110.dp, 280.dp)
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth()) {
                        Image(painterResource(artwork.background), null, Modifier.matchParentSize().testTag("onboarding_scene_background"), contentScale = ContentScale.Crop)
                        Column {
                            CustomizationHeader(onBack, artwork, saving = saving, title = "Всё готово!", backDescription = "Назад к аксессуарам")
                            Box(Modifier.fillMaxWidth().height(stageHeight), contentAlignment = Alignment.Center) {
                                Image(painterResource(portrait), "Твой спутник готов к приключениям",
                                    Modifier.size(stageHeight + 20.dp).offset(y = (-8).dp))
                            }
                            Box(Modifier.fillMaxWidth().height(24.dp).background(IntroCream,
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
                        }
                    }
                    IntroductionContent(artwork, Modifier.weight(1f).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                        icons = icons, onStart = onStart, saving = saving, saveFailed = saveFailed)
                }
            }
        }
    }
}

private data class MoneyConcept(val title: String, val detail: String, val icon: String, val tint: Color)
private val concepts = listOf(
    MoneyConcept("Нужно", "Еда, уход и всё, без чего питомцу не обойтись.", "basket", Color(0xffedf4d8)),
    MoneyConcept("Хочу", "Игрушки, книги и развлечения — покупки, которые можно отложить.", "diamond", Color(0xffeeebf8)),
    MoneyConcept("В копилку", "Планируем, сколько сберечь для цели. Затем откроем копилку и положим туда эту сумму.", "goal", Color(0xffedf4d8)),
    MoneyConcept("Запас", "Поможет, если в дороге понадобится ремонт или лечение.", "reserve", Color(0xffeeebf8)),
)

@Composable
private fun IntroductionContent(
    artwork: CustomizationArtwork, modifier: Modifier, centerContent: Boolean = false,
    icons: Map<String, Int>, onStart: () -> Unit, saving: Boolean, saveFailed: Boolean,
    scrollAll: Boolean = false,
) {
    val density = LocalDensity.current
    var actionHeight by remember { mutableStateOf(88.dp) }
    BoxWithConstraints(modifier) {
        val grid = maxWidth >= 540.dp
        val compact = maxHeight < 480.dp
        val topSpacing = if (compact) 8.dp else 12.dp
        val availableHeight = if (scrollAll) 0.dp else (maxHeight - actionHeight - 8.dp - topSpacing).coerceAtLeast(0.dp)
        val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().then(if (scrollAll) Modifier else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            .padding(horizontal = 22.dp).padding(top = topSpacing, bottom = if (scrollAll) 8.dp else actionHeight + 8.dp)) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 680.dp).fillMaxWidth()
                .heightIn(min = if (centerContent) availableHeight else 0.dp),
                verticalArrangement = if (centerContent) Arrangement.Center else Arrangement.Top) {
                Text("Впереди — приключения!", fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold,
                    color = IntroInk, fontSize = 24.sp, lineHeight = 29.sp)
                Spacer(Modifier.height(8.dp))
                Text("Вместе со спутником исследуй мир, помогай друзьям и собирай снаряжение. Решай, что купить сейчас, а на что копить.",
                    color = IntroMuted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
                if (grid) {
                    concepts.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { ConceptCard(it, icons.getValue(it.icon), artwork, Modifier.weight(1f).fillMaxHeight(), true) }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    concepts.forEach {
                        ConceptCard(it, icons.getValue(it.icon), artwork, Modifier.fillMaxWidth(), false)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        }
        val action: @Composable (Modifier) -> Unit = { actionModifier ->
        Column(actionModifier.fillMaxWidth().onSizeChanged {
            actionHeight = with(density) { it.height.toDp() }
        }.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = if (compact) 8.dp else 16.dp)) {
            if (saveFailed) Text("Не удалось сохранить настройки. Попробуй ещё раз.",
                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onStart, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 3.dp, disabledElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xffa8e830), contentColor = IntroInk,
                    disabledContainerColor = Color(0xffdedbe3), disabledContentColor = IntroMuted)) {
                Text(if (saving) "Сохраняем…" else "Начать приключение", fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        }
        if (scrollAll) Column { body(); action(Modifier) }
        else { body(); action(Modifier.align(Alignment.BottomCenter)) }
    }
}

@Composable
private fun ConceptCard(concept: MoneyConcept, icon: Int, artwork: CustomizationArtwork, modifier: Modifier, grid: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    val card = modifier.background(Color.White, shape).border(1.dp, Color(0xffe1deeb), shape).padding(12.dp)
    val badge: @Composable () -> Unit = {
        Box(Modifier.size(48.dp).background(concept.tint, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(30.dp), tint = IntroInk)
        }
    }
    val copy: @Composable () -> Unit = {
        Text(concept.title, color = IntroInk, fontFamily = artwork.titleFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(3.dp))
        Text(concept.detail, color = IntroMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
    if (grid) {
        Column(card) { badge(); Spacer(Modifier.height(12.dp)); copy() }
    } else {
        Row(card, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            badge()
            Column(Modifier.weight(1f)) { copy() }
        }
    }
}
