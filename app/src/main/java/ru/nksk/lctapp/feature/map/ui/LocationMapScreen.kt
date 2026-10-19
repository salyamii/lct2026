package ru.nksk.lctapp.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.*
import ru.nksk.lctapp.core.ui.components.GameBackButton
import ru.nksk.lctapp.core.ui.components.GameHomeButton
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.components.GameInk

/** Presentation only: coordinates and artwork do not define unlock or travel rules. */
@Immutable
data class MapLocationUi(
    val id: String,
    val title: String,
    val bounds: Rect,
    val isUnlocked: Boolean = true,
)

@Composable
fun LocationMapScreen(
    locations: List<MapLocationUi>,
    currentLocationId: String,
    onLocationClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    onHome: () -> Unit = onBack,
) {
    Box(modifier.fillMaxSize().background(AdventureNight), contentAlignment = Alignment.TopCenter) {
        MapViewport(locations, currentLocationId, onLocationClick, onBack,
            Modifier.fillMaxSize(), interactionEnabled, onHome)
    }
}

/** Artwork and landmark targets share one full-screen canvas; controls float above it. */
@Composable
private fun MapViewport(
    locations: List<MapLocationUi>,
    currentLocationId: String,
    onLocationClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    interactionEnabled: Boolean,
    onHome: () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val size = mapCanvasSize(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        val canvasHeight = size.height.dp
        val canvasWidth = size.width.dp
        val scroll = rememberScrollState()
        val canScrollUp by remember(size.scrollable, scroll) {
            derivedStateOf { size.scrollable && scroll.canScrollBackward }
        }
        val canScrollDown by remember(size.scrollable, scroll) {
            derivedStateOf {
                size.scrollable && scroll.maxValue != Int.MAX_VALUE && scroll.canScrollForward
            }
        }
        Box(
            Modifier.fillMaxSize().then(if (size.scrollable) Modifier.verticalScroll(scroll) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(canvasWidth, canvasHeight)) {
                MapLandscape(Modifier.matchParentSize())
                locations.forEach { location ->
                    val bounds = location.bounds
                    val targetWidth = (canvasWidth * bounds.width)
                        .coerceAtLeast((canvasWidth * .43f).coerceAtMost(170.dp))
                    key(location.id) {
                        LocationTarget(
                            location, location.id == currentLocationId,
                            artworkHeight = canvasHeight * bounds.height,
                            enabled = interactionEnabled,
                            onClick = { onLocationClick(location.id) },
                            modifier = Modifier.offset(
                                x = (canvasWidth * bounds.center.x - targetWidth / 2)
                                    .coerceIn(0.dp, canvasWidth - targetWidth),
                                y = canvasHeight * bounds.top,
                            ).width(targetWidth),
                        )
                    }
                }
            }
        }
        MapHeader(onBack, onHome, interactionEnabled,
            Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding())
        Box(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(12.dp)) {
            MapInformationButton()
        }
        if (canScrollUp || canScrollDown) {
            Column(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canScrollUp) MapScrollHint(up = true)
                if (canScrollDown) MapScrollHint(up = false)
            }
        }
    }
}

@Composable
private fun MapScrollHint(up: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape)
        .background(AdventurePanel)
        .border(1.dp, AdventureLabel.copy(alpha = .45f), CircleShape)
        .semantics {
            contentDescription = if (up) "Выше есть ещё локации. Прокрути карту вверх."
                else "Ниже есть ещё локации. Прокрути карту вниз."
        },
        contentAlignment = Alignment.Center) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.menu_chevron), null,
            Modifier.size(22.dp).rotate(if (up) -90f else 90f), tint = AdventureLabel)
    }
}

@Composable
private fun MapHeader(onBack: () -> Unit, onHome: () -> Unit, interactionEnabled: Boolean,
    modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        val showHomeLabel = maxWidth >= 460.dp && LocalDensity.current.fontScale < 1.3f
        val sideWidth = if (showHomeLabel) 116.dp else 48.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.width(sideWidth), contentAlignment = Alignment.CenterStart) {
                GameBackButton(onBack, Modifier.size(48.dp), enabled = interactionEnabled)
            }
            GamePlaque(Modifier.weight(1f).heightIn(min = 64.dp),
                artworkRes = R.drawable.menu_name_plaque_v3, fixedEdges = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                Text("Карта\nприключений", color = GameInk, fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 19.sp,
                    textAlign = TextAlign.Center)
            }
            Box(Modifier.width(sideWidth), contentAlignment = Alignment.CenterEnd) {
                GameHomeButton(onHome,
                    modifier = if (showHomeLabel) Modifier else Modifier.size(48.dp),
                    enabled = interactionEnabled, showLabel = showHomeLabel)
            }
        }
    }
}

@Preview(name = "Карта целиком", widthDp = 390, heightDp = 844)
@Preview(name = "Невысокий экран", widthDp = 360, heightDp = 640)
@Preview(name = "Альбомный экран", widthDp = 844, heightDp = 390)
@Preview(name = "Крупный текст", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Composable
private fun LocationMapPreview() {
    LCTAppTheme { LocationMapScreen(mapLocations, "city", {}, {}) }
}

@Composable
private fun MapInformationButton() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val popupGap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val popupPosition = remember(popupGap) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.right - popupContentSize.width)
                    .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                val y = (anchorBounds.top - popupContentSize.height - popupGap).coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }
    Box {
        GamePlaque(Modifier.size(52.dp), light = true, fixedEdges = true,
            contentPadding = PaddingValues(0.dp)) {
            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxSize()
                .semantics { contentDescription = "Информация о карте" }) {
                Text("i", color = GameInk, fontFamily = Rubik, fontSize = 26.sp,
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
        if (expanded) {
            Popup(
                popupPositionProvider = popupPosition,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                GamePlaque(Modifier.widthIn(max = 260.dp).semantics { paneTitle = "Информация о карте" },
                    light = true, fixedEdges = true,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                    Text("Выбери место на карте, куда отправимся", color = GameInk,
                        fontFamily = Nunito, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun LocationTarget(
    location: MapLocationUi,
    current: Boolean,
    enabled: Boolean,
    artworkHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLockedInfo by rememberSaveable(location.id) { mutableStateOf(false) }
    val selectedLocation = current && location.isUnlocked
    val labelColor = when {
        !location.isUnlocked -> Color(0xFFB8BAC6)
        selectedLocation -> AdventureLime
        else -> AdventureLabel
    }
    // One target for artwork and label. Locked taps explain availability only.
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = if (location.isUnlocked) "Отправиться сюда" else "Узнать, как открыть",
            ) {
                if (location.isUnlocked) onClick() else showLockedInfo = true
            }
            .semantics(mergeDescendants = true) {
                selected = selectedLocation
                stateDescription = if (location.isUnlocked) "Доступна" else "Закрыта по сюжету"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.fillMaxWidth().height(artworkHeight + 4.dp))
        Row(
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (location.isUnlocked) AdventurePanel.copy(alpha = .93f) else Color(0xFF292C39))
                .border(1.dp, if (selectedLocation) AdventureLime else Color.White.copy(alpha = .20f),
                    RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (!location.isUnlocked) Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.customization_lock),
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                location.title, color = labelColor,
                fontFamily = Nunito, fontSize = 13.sp, lineHeight = 17.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
    if (showLockedInfo && !location.isUnlocked) {
        AlertDialog(
            onDismissRequest = { showLockedInfo = false },
            title = { Text(location.title) },
            text = { Text("Это место откроется по ходу истории.") },
            confirmButton = {
                TextButton(onClick = { showLockedInfo = false }) { Text("Понятно") }
            },
        )
    }
}
