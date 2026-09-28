package ru.nksk.lctapp.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.*
import kotlinx.coroutines.launch

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
) {
    Box(modifier.fillMaxSize().background(AdventureNight), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(AdventurePanel)
                        .border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
                        .clickable(enabled = interactionEnabled, role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = "Назад" },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(androidx.compose.ui.res.painterResource(R.drawable.menu_chevron),
                        contentDescription = null, modifier = Modifier.size(18.dp).rotate(180f))
                }
                Text("Карта приключений", color = AdventureLabel, fontFamily = Rubik,
                    fontSize = 21.sp, lineHeight = 25.sp, modifier = Modifier.weight(1f))
                MapInformationButton()
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val scroll = rememberScrollState()
                val scrollScope = rememberCoroutineScope()
                val scrollPage = with(LocalDensity.current) { maxHeight.toPx() * .75f }
                val canScrollUp by remember { derivedStateOf { scroll.canScrollBackward } }
                val canScrollDown by remember { derivedStateOf {
                    scroll.maxValue != Int.MAX_VALUE && scroll.canScrollForward
                } }
                val canvasHeight = maxWidth * (1844f / 853f)
                val canvasWidth = maxWidth
                Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    Box(Modifier.fillMaxWidth().height(canvasHeight)) {
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
                if (canScrollUp) MapScrollArrow(up = true, enabled = interactionEnabled,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                    scrollScope.launch { scroll.animateScrollBy(-scrollPage) }
                }
                if (canScrollDown) MapScrollArrow(up = false, enabled = interactionEnabled,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                    scrollScope.launch { scroll.animateScrollBy(scrollPage) }
                }
            }
        }
    }
}

@Composable
private fun MapScrollArrow(up: Boolean, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape)
        .background(AdventurePanel)
        .border(1.dp, AdventureLabel.copy(alpha = .45f), CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (up) "Прокрутить карту вверх" else "Прокрутить карту вниз" },
        contentAlignment = Alignment.Center) {
        Icon(androidx.compose.ui.res.painterResource(R.drawable.menu_chevron), null,
            Modifier.size(22.dp).rotate(if (up) -90f else 90f), tint = AdventureLabel)
    }
}

@Composable
private fun MapInformationButton() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val popupOffset = with(LocalDensity.current) { 60.dp.roundToPx() }
    Box {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(AdventurePanel)
                .border(1.dp, AdventureLavender.copy(alpha = .6f), CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Показать подсказку") { expanded = !expanded }
                .semantics { contentDescription = "Информация о карте" },
            contentAlignment = Alignment.Center,
        ) {
            Text("i", color = AdventureLavender, fontFamily = Rubik, fontSize = 27.sp,
                modifier = Modifier.clearAndSetSemantics {})
        }
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, popupOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Text(
                    "Выбери место на карте, куда отправимся",
                    modifier = Modifier.widthIn(max = 260.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(AdventurePanel)
                        .border(1.dp, AdventureLavender.copy(alpha = .4f), RoundedCornerShape(16.dp))
                        .semantics { paneTitle = "Информация о карте" }
                        .padding(16.dp),
                    color = AdventureLabel, fontFamily = Nunito,
                    fontSize = 14.sp, lineHeight = 20.sp,
                )
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
