package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xff231942)
private val Cream = Color(0xfffbfaef)
private val Muted = Color(0xff79738b)
private val Lime = Color(0xffa8e830)
private val Line = Color(0xffe1deeb)

enum class CharacterTemperament(val label: String) {
    Curious("Любопытный"), Confident("Уверенный"), Joyful("Радостный"),
}

enum class CharacterFur(val label: String, val swatch: Color) {
    Copper("Медный", Color(0xffcf722e)),
    Sand("Песочный", Color(0xffd6b27a)),
    Russet("Тёмно-рыжий", Color(0xff7e3429)),
}

/** Presentation values mapped to the saved profile by the app entry. */
@Immutable
data class CustomizationUiState(
    val name: String = "",
    val temperament: CharacterTemperament = CharacterTemperament.Curious,
    val fur: CharacterFur = CharacterFur.Copper,
)

/** Renders customization without owning navigation or persistence. */
@Composable
fun CustomizationScreen(
    state: CustomizationUiState,
    artwork: CustomizationArtwork,
    onNameChange: (String) -> Unit,
    onTemperamentChange: (CharacterTemperament) -> Unit,
    onFurChange: (CharacterFur) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    var nameError by rememberSaveable { mutableStateOf(false) }
    val editName: (String) -> Unit = {
        nameError = false
        onNameChange(it)
    }
    val continueWithName: () -> Unit = {
        if (state.name.isBlank()) nameError = true else onContinue()
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Cream).imePadding()) {
        val compactHeight = maxHeight < 480.dp
        if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    Image(painterResource(artwork.background), null,
                        Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    CharacterStage(state, artwork,
                        Modifier.size(minOf(maxWidth * .95f, maxHeight * .75f, 520.dp))
                            .align(Alignment.Center), maxCanvas = 520.dp)
                    CustomizationHeader(onBack, artwork, saving)
                }
                Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)),
                    contentAlignment = Alignment.Center) {
                    CustomizationEditor(state, artwork, editName, onTemperamentChange,
                        onFurChange, saving, saveFailed, continueWithName,
                        Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .fillMaxHeight().padding(top = if (compactHeight) 8.dp else 24.dp),
                        compactHeight = compactHeight, nameError = nameError)
                }
            }
        } else {
            // Keep the scene outside the form's scroll container. Shrink it with available
            // height (including IME) so the editor retains space on compact windows.
            val stageHeight = (maxHeight * .30f).coerceIn(100.dp, 300.dp)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth()) {
                    Image(painterResource(artwork.background), null,
                        Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Column {
                        CustomizationHeader(onBack, artwork, saving)
                        CharacterStage(state, artwork, Modifier.fillMaxWidth().height(stageHeight))
                        Box(Modifier.fillMaxWidth().height(28.dp)
                            .background(Cream, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
                    }
                }
                CustomizationEditor(state, artwork, editName, onTemperamentChange,
                    onFurChange, saving, saveFailed, continueWithName,
                    Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                    compactHeight = compactHeight, nameError = nameError)
            }
        }
    }
}

/** The action floats over the form; end padding keeps its final fields reachable. */
@Composable
private fun CustomizationEditor(
    state: CustomizationUiState,
    artwork: CustomizationArtwork,
    onNameChange: (String) -> Unit,
    onTemperamentChange: (CharacterTemperament) -> Unit,
    onFurChange: (CharacterFur) -> Unit,
    saving: Boolean,
    saveFailed: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier,
    compactHeight: Boolean,
    nameError: Boolean,
) {
    val density = LocalDensity.current
    var actionHeight by remember { mutableStateOf(84.dp) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)
            .padding(bottom = actionHeight)) {
            CustomizationFields(state, artwork, onNameChange, onTemperamentChange, onFurChange, saving, nameError)
        }
        CustomizationContinue(artwork, saving, saveFailed, {
            onContinue()
            if (state.name.isBlank()) scope.launch { scroll.animateScrollTo(0) }
        },
            Modifier.align(Alignment.BottomCenter).onSizeChanged {
                actionHeight = with(density) { it.height.toDp() }
            }, bottomSpacing = if (compactHeight) 8.dp else 16.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizationFields(
    state: CustomizationUiState,
    artwork: CustomizationArtwork,
    onNameChange: (String) -> Unit,
    onTemperamentChange: (CharacterTemperament) -> Unit,
    onFurChange: (CharacterFur) -> Unit,
    saving: Boolean,
    nameError: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasVisible by remember { mutableStateOf(imeVisible) }
    var nameFocused by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        // Only react to dismissal, not the hidden frame before a newly focused field opens IME.
        if (imeWasVisible && !imeVisible && nameFocused) focusManager.clearFocus()
        imeWasVisible = imeVisible
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Text("Познакомься со своим спутником", fontFamily = artwork.titleFont,
            fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 25.sp, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("Выбери имя, характер и цвет шерсти", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(value = state.name, onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Имя спутника" }
                .onFocusChanged { nameFocused = it.isFocused },
            singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboard?.hide()
            }),
            isError = nameError,
            placeholder = { Text("Как меня зовут?") }, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink, unfocusedTextColor = Ink,
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedBorderColor = Color(0xff7560ba), unfocusedBorderColor = Line,
                focusedLabelColor = Ink, unfocusedLabelColor = Muted, cursorColor = Ink))
        if (nameError) {
            Text("Введи имя спутника", color = MaterialTheme.colorScheme.error, fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Характер", artwork)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CharacterTemperament.entries.forEach { item ->
                val selected = item == state.temperament
                Box(Modifier.weight(1f).heightIn(min = 54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) Ink else Color.White)
                    .border(1.dp, if (selected) Ink else Line, RoundedCornerShape(16.dp))
                    .selectable(selected, enabled = !saving, role = Role.RadioButton, onClick = { onTemperamentChange(item) })
                    .padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(item.label, fontFamily = artwork.bodyFont, fontSize = 12.sp, lineHeight = 16.sp,
                        textAlign = TextAlign.Center, color = if (selected) Color.White else Ink)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel("Цвет шерсти", artwork)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CharacterFur.entries.forEach { item ->
                val selected = item == state.fur
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (selected) Color(0xffedf4d8) else Color.Transparent)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Color(0xff88a940) else Color.Transparent,
                        RoundedCornerShape(18.dp))
                    .selectable(selected, enabled = !saving, role = Role.RadioButton, onClick = { onFurChange(item) })
                    .padding(horizontal = 3.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(42.dp).border(3.dp, Color.White, CircleShape)
                        .padding(3.dp).background(item.swatch, CircleShape))
                    Spacer(Modifier.height(7.dp))
                    Text(item.label, color = Ink, fontSize = 12.sp, lineHeight = 16.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CustomizationContinue(
    artwork: CustomizationArtwork,
    saving: Boolean,
    saveFailed: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    bottomSpacing: Dp = 16.dp,
) {
    Column(modifier.fillMaxWidth()
        .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = bottomSpacing)) {
        if (saveFailed) {
            Text("Не удалось сохранить настройки. Попробуй ещё раз.", color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        Button(onClick = onContinue, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(18.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 3.dp,
                disabledElevation = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink,
                disabledContainerColor = Color(0xffdedbe3), disabledContentColor = Muted)) {
            Text(if (saving) "Сохраняем…" else "Продолжить", fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }
    }
}

@Composable
fun CustomizationHeader(
    onBack: () -> Unit,
    artwork: CustomizationArtwork,
    saving: Boolean = false,
    title: String = "Создай образ",
    backDescription: String = "Назад к выбору персонажа",
) {
    val background = Color(0xff171440).copy(alpha = .63f)
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keep a 48 dp touch target around the 40 dp circle from the design.
        IconButton(onClick = onBack, enabled = !saving, modifier = Modifier.size(48.dp)) {
            Box(
                Modifier.size(40.dp).background(background, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = .2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(artwork.chevron), backDescription,
                    Modifier.size(20.dp).rotate(180f), tint = Color.White)
            }
        }
        Text(
            title,
            modifier = Modifier.background(background, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            fontFamily = artwork.titleFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun SectionLabel(text: String, artwork: CustomizationArtwork) {
    Text(text, color = Ink, fontFamily = artwork.bodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
}

@Composable
private fun CharacterStage(
    state: CustomizationUiState,
    artwork: CustomizationArtwork,
    modifier: Modifier = Modifier.fillMaxWidth().height(300.dp),
    maxCanvas: Dp = 320.dp,
) {
    BoxWithConstraints(modifier) {
        val canvasSize = minOf(maxWidth, maxHeight + 20.dp, maxCanvas)
        Image(painterResource(artwork.image(state.fur)), "Образ спутника: ${state.fur.label}",
            Modifier.requiredSize(canvasSize).align(Alignment.Center).offset(y = (-14).dp), contentScale = ContentScale.Fit)
        Text("${state.name.ifBlank { "Твой спутник" }} · ${state.temperament.label}",
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 10.dp)
                .background(Ink.copy(alpha = .9f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color.White, fontFamily = artwork.bodyFont, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
