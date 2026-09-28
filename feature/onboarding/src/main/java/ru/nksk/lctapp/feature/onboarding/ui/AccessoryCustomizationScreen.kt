package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import androidx.compose.ui.unit.sp


private val AccessoryInk = Color(0xff231942)
private val AccessoryCream = Color(0xfffbfaef)
private val AccessoryMuted = Color(0xff79738b)
private val AccessoryLime = Color(0xffa8e830)
private val AccessoryCardShape = RoundedCornerShape(18.dp)

enum class OnboardingAccessory(val label: String, val id: String, val available: Boolean = true) {
    None("Без аксессуара", "PLAIN"), Backpack("Рюкзак", "BACKPACK"), Bandana("Бандана", "BANDANA"),
    Lantern("Фонарик", "LANTERN", false), Patch("Маршрутный патч", "PATCH", false),
    Hat("Шляпа", "HAT", false), Goggles("Очки", "GLASSES", false),
    Compass("Компас", "COMPASS", false), Binoculars("Бинокль", "BINOCULARS", false),
}

@Immutable
data class AccessoryArtwork(
    val noneIcon: Int,
    val lockIcon: Int,
    val thumbnails: Map<OnboardingAccessory, Int>,
    val portraits: Map<OnboardingAccessory, Map<CharacterFur, Int>>,
)

@Immutable
data class AccessoryCustomizationUiState(
    val name: String = "Рыжик",
    val fur: CharacterFur = CharacterFur.Copper,
    val accessory: OnboardingAccessory = OnboardingAccessory.Backpack,
)

/** Renders the accessory step; app wiring owns persistence and navigation. */
@Composable
fun AccessoryCustomizationScreen(
    state: AccessoryCustomizationUiState,
    artwork: CustomizationArtwork,
    accessories: AccessoryArtwork,
    onSelect: (OnboardingAccessory) -> Unit,
    onBack: () -> Unit,
    onApply: () -> Unit,
    saving: Boolean = false,
    saveFailed: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(AccessoryCream)) {
        val compactHeight = maxHeight < 480.dp
        // In short windows the whole page scrolls, including its action. A scene
        // cannot consume the controls' height while the keyboard/window is small.
        if (needsOnboardingScroll(maxHeight)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth()) {
                    Image(painterResource(artwork.background), null,
                        Modifier.matchParentSize().testTag("onboarding_scene_background"), contentScale = ContentScale.Crop)
                    Column {
                        CustomizationHeader(onBack, artwork, saving = saving, title = "Выбор аксессуара",
                            backDescription = "Назад к образу спутника")
                        Image(painterResource(accessories.portraits.getValue(state.accessory).getValue(state.fur)),
                            "${state.name}: ${state.accessory.label}",
                            Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Fit)
                    }
                }
                Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)), contentAlignment = Alignment.TopCenter) {
                    AccessoryControls(state, artwork, accessories, onSelect, onApply,
                        Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(top = 16.dp),
                        compactHeight, saving, saveFailed, scrollAll = true)
                }
            }
        } else if (maxWidth >= 720.dp) {
            Row(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    Image(painterResource(artwork.background), null,
                        Modifier.matchParentSize().testTag("onboarding_scene_background"), contentScale = ContentScale.Crop)
                    Image(painterResource(accessories.portraits.getValue(state.accessory).getValue(state.fur)),
                        "${state.name}: ${state.accessory.label}",
                        Modifier.size(minOf(maxWidth * .95f, maxHeight * .75f, 520.dp))
                            .align(Alignment.Center), contentScale = ContentScale.Fit)
                    CustomizationHeader(onBack, artwork, saving = saving, title = "Выбор аксессуара",
                        backDescription = "Назад к образу спутника")
                }
                Box(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)),
                    contentAlignment = Alignment.Center) {
                    AccessoryControls(state, artwork, accessories, onSelect, onApply,
                        Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight()
                            .padding(top = if (compactHeight) 8.dp else 24.dp), compactHeight, saving, saveFailed)
                }
            }
        } else {
            val stageHeight = (maxHeight * .30f).coerceIn(120.dp, 300.dp)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth()) {
                    Image(painterResource(artwork.background), null,
                        Modifier.matchParentSize().testTag("onboarding_scene_background"), contentScale = ContentScale.Crop)
                    Column {
                        CustomizationHeader(onBack, artwork, saving = saving, title = "Выбор аксессуара",
                            backDescription = "Назад к образу спутника")
                        BoxWithConstraints(Modifier.fillMaxWidth().height(stageHeight)) {
                            Image(painterResource(accessories.portraits.getValue(state.accessory).getValue(state.fur)),
                                "${state.name}: ${state.accessory.label}",
                                Modifier.requiredSize(minOf(maxWidth, stageHeight + 20.dp))
                                    .align(Alignment.Center).offset(y = (-14).dp), contentScale = ContentScale.Fit)
                        }
                        Box(Modifier.fillMaxWidth().height(28.dp)
                            .background(AccessoryCream, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
                    }
                }
                AccessoryControls(state, artwork, accessories, onSelect, onApply,
                    Modifier.fillMaxWidth().weight(1f).windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
                    compactHeight, saving, saveFailed)
            }
        }
    }
}

@Composable
private fun AccessoryControls(
    state: AccessoryCustomizationUiState,
    artwork: CustomizationArtwork,
    accessories: AccessoryArtwork,
    onSelect: (OnboardingAccessory) -> Unit,
    onApply: () -> Unit,
    modifier: Modifier,
    compactHeight: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    scrollAll: Boolean = false,
) {
    val density = LocalDensity.current
    var actionHeight by remember { mutableStateOf(84.dp) }
    BoxWithConstraints(modifier) {
        // A short window can scroll the controls instead of crushing the artwork.
        val minimumCarouselHeight = 220.dp * density.fontScale.coerceAtLeast(1f)
        val carouselHeight = if (scrollAll) minimumCarouselHeight else
            (maxHeight - actionHeight - if (compactHeight) 31.dp else 89.dp).coerceAtLeast(minimumCarouselHeight)
        val controls: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().then(if (scrollAll) Modifier else
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
                .padding(horizontal = 16.dp).padding(bottom = if (scrollAll) 0.dp else actionHeight)) {
            Text("Добавь деталь к образу", color = AccessoryInk,
                fontFamily = artwork.titleFont, fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp, lineHeight = 25.sp)
            Spacer(Modifier.height(6.dp))
            if (!compactHeight) {
                Text("Примерь аксессуар своему спутнику. Можно выбрать только один.",
                    color = AccessoryMuted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(18.dp))
            }
            AccessoryCarousel(state, artwork, accessories, onSelect,
                Modifier.fillMaxWidth().height(carouselHeight), saving)
            }
        }
        val action: @Composable (Modifier) -> Unit = { actionModifier ->
        Column(actionModifier.fillMaxWidth().onSizeChanged {
            actionHeight = with(density) { it.height.toDp() }
        }.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = if (compactHeight) 8.dp else 16.dp)) {
            if (saveFailed) Text("Не удалось сохранить настройки. Попробуй ещё раз.",
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp))
            Button(onClick = onApply, enabled = !saving && state.accessory.available,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 3.dp,
                    disabledElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccessoryLime, contentColor = AccessoryInk,
                    disabledContainerColor = Color(0xffdedbe3), disabledContentColor = AccessoryMuted)) {
                Text(if (saving) "Сохраняем…" else "Применить", fontFamily = artwork.titleFont,
                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }
        }
        }
        if (scrollAll) Column { controls(); action(Modifier) }
        else { controls(); action(Modifier.align(Alignment.BottomCenter)) }
    }
}

/** The page nearest the center previews its item; locked items cannot be applied. */
@Composable
private fun AccessoryCarousel(
    state: AccessoryCustomizationUiState,
    artwork: CustomizationArtwork,
    accessories: AccessoryArtwork,
    onSelect: (OnboardingAccessory) -> Unit,
    modifier: Modifier,
    saving: Boolean,
) {
    val items = OnboardingAccessory.entries
    val pager = rememberPagerState(initialPage = items.indexOf(state.accessory), pageCount = { items.size })
    val latestOnSelect = rememberUpdatedState(onSelect)
    LaunchedEffect(pager.currentPage) {
        latestOnSelect.value(items[pager.currentPage])
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Measure both messages so their longest wrapping reserves space before swiping.
        Box(Modifier.fillMaxWidth().testTag("accessory_hint").padding(bottom = 4.dp), contentAlignment = Alignment.TopCenter) {
            listOf(
                true to "Листай, чтобы примерить аксессуар",
                false to "Этот предмет сейчас недоступен. Ты сможешь получить его во время прохождения.",
            ).forEach { (available, message) ->
                val visible = items[pager.currentPage].available == available
                Text(message, color = AccessoryMuted, fontSize = 12.sp, lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().then(
                        if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }))
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Center one page while fitting whole neighbours, including their shadow margins.
            val visiblePages = when {
                maxWidth >= 592.dp -> 5
                maxWidth >= 328.dp -> 3
                else -> 1
            }
            val pageWidth = if (visiblePages == 1) minOf(maxWidth, 208.dp)
                else (maxWidth - 8.dp * (visiblePages - 1)) / visiblePages
            val artworkSize = minOf(144.dp, pageWidth - 32.dp, maxHeight - 70.dp)
                .coerceAtLeast(64.dp)
            HorizontalPager(
                state = pager,
                userScrollEnabled = !saving,
                modifier = Modifier.fillMaxSize().testTag("accessory_pager"),
                pageSize = PageSize.Fixed(pageWidth),
                contentPadding = PaddingValues(horizontal = (maxWidth - pageWidth) / 2),
                pageSpacing = 8.dp,
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val item = items[page]
                // Transform the card and its shadow together, with room for the shadow.
                // Availability alone controls dimming; side pages keep their original colors.
                Box(Modifier.fillMaxWidth().graphicsLayer {
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val distance = offset.absoluteValue
                    scaleX = 1f - .14f * distance
                    scaleY = 1f - .14f * distance
                    translationY = 10.dp.toPx() * distance
                    rotationY = offset * 8f
                    cameraDistance = 12f * density
                    compositingStrategy = CompositingStrategy.Offscreen
                }.padding(8.dp)) {
                    AccessoryTile(item, item == state.accessory, artwork, accessories,
                        modifier = Modifier.fillMaxWidth().shadow(4.dp, AccessoryCardShape, clip = false),
                        imageSize = artworkSize)
                }
            }
        }
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            repeat(items.size) { index ->
                Box(Modifier.size(if (index == pager.currentPage) 8.dp else 6.dp)
                    .align(Alignment.CenterVertically)
                    .background(if (index == pager.currentPage) AccessoryInk else Color(0xffd3cfdd), CircleShape))
            }
            Text("${pager.currentPage + 1} из ${items.size}", color = AccessoryMuted, fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun AccessoryTile(
    item: OnboardingAccessory,
    selected: Boolean,
    artwork: CustomizationArtwork,
    accessories: AccessoryArtwork,
    modifier: Modifier,
    imageSize: Dp = 64.dp,
) {
    Box(modifier.clip(AccessoryCardShape)
        .background(when { !item.available -> Color(0xffefedf1); selected -> Color(0xffedf4d8); else -> Color.White })
        .border(if (selected) 2.dp else 1.dp,
            if (selected && item.available) Color(0xff88a940) else if (selected) AccessoryMuted else Color(0xffe1deeb), AccessoryCardShape)
        .semantics { if (!item.available) stateDescription = "Закрыто" }
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(imageSize), contentAlignment = Alignment.Center) {
                if (item == OnboardingAccessory.None) {
                    Icon(painterResource(accessories.noneIcon), null,
                        Modifier.size(28.dp), tint = AccessoryMuted)
                } else {
                    Image(painterResource(accessories.thumbnails.getValue(item)), null,
                        Modifier.fillMaxSize().alpha(if (item.available) 1f else .45f),
                        colorFilter = if (item.available) null else ColorFilter.colorMatrix(
                            ColorMatrix().apply { setToSaturation(0f) }),
                        contentScale = ContentScale.Fit)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.label, color = if (item.available) AccessoryInk else AccessoryMuted,
                fontFamily = artwork.bodyFont, fontSize = 12.sp, lineHeight = 16.sp,
                textAlign = TextAlign.Center, minLines = 2)
        }
        if (!item.available) {
            Icon(painterResource(accessories.lockIcon), null,
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp), tint = AccessoryMuted)
        }
    }
}
