package ru.nksk.lctapp.feature.gear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import kotlin.math.roundToInt

/** A view of an owned occurrence. Navigation and equipment changes belong to the caller. */
@Composable
internal fun GearItemDetails(
    item: GearItemUiState,
    pageId: String?,
    onPageSelected: (String) -> Unit,
    onClose: () -> Unit,
    onEquip: (String) -> Unit,
    busy: Boolean,
    actionMessage: Int? = null,
) {
    val pages = item.pages.ifEmpty {
        listOf(GearContentPage("overview", item.name, item.artworkRes, item.description))
    }
    val pageIndex = pages.indexOfFirst { it.id == pageId }.coerceAtLeast(0)
    val page = pages[pageIndex]
    val scroll = rememberScrollState()
    LaunchedEffect(item.occurrenceId, page.id) { scroll.scrollTo(0) }

    Surface(modifier = Modifier.fillMaxSize(), color = GearColors.Cream) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.safeDrawingPadding().widthIn(max = 840.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    item.name,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    color = GearColors.Ink, fontFamily = Rubik,
                    fontWeight = FontWeight.Bold, fontSize = 23.sp,
                )
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Закрыть", color = GearColors.Ink, fontFamily = Nunito,
                        fontWeight = FontWeight.Bold)
                }
            }
            actionMessage?.let { message ->
                Text(
                    stringResource(message),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                        .background(GearColors.Gold, RoundedCornerShape(12.dp)).padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    color = GearColors.Ink, fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp,
                )
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (pages.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pages.forEachIndexed { index, candidate ->
                            Button(
                                onClick = { onPageSelected(candidate.id) },
                                modifier = Modifier.heightIn(min = 48.dp)
                                    .semantics { selected = index == pageIndex },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (index == pageIndex) GearColors.Ink else GearColors.Card,
                                    contentColor = if (index == pageIndex) GearColors.Card else GearColors.Ink,
                                ),
                            ) {
                                Text(candidate.title, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        "${pageIndex + 1} из ${pages.size}", color = GearColors.Secondary,
                        fontFamily = Nunito, fontSize = 14.sp,
                    )
                }
                key(item.occurrenceId, page.id, page.imageRes) {
                    page.imageRes?.let { resource ->
                        GearDetailArtwork(resource, page.title, page.zoomable)
                    }
                }
                if (page.title != item.name) {
                    Text(
                        page.title, modifier = Modifier.semantics { heading() },
                        color = GearColors.Ink, fontFamily = Rubik,
                        fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    )
                }
                if (page.body.isNotBlank()) {
                    Text(
                        page.body, color = GearColors.Ink, fontFamily = Nunito,
                        fontSize = 17.sp, lineHeight = 25.sp,
                    )
                }
                if (pages.size > 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = { onPageSelected(pages[pageIndex - 1].id) },
                            enabled = pageIndex > 0,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = GearColors.Ink),
                        ) { Text("Предыдущая", fontFamily = Nunito, textAlign = TextAlign.Center) }
                        TextButton(
                            onClick = { onPageSelected(pages[pageIndex + 1].id) },
                            enabled = pageIndex < pages.lastIndex,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = GearColors.Ink),
                        ) { Text("Следующая", fontFamily = Nunito, textAlign = TextAlign.Center) }
                    }
                }
                item.lookId?.let { lookId ->
                    Button(
                        onClick = { onEquip(if (item.equipped) "PLAIN" else lookId) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdventureLime, contentColor = GearColors.Ink,
                            disabledContainerColor = AdventureLime, disabledContentColor = GearColors.Ink,
                        ),
                    ) {
                        Text(
                            stringResource(if (item.equipped) R.string.gear_unequip else R.string.gear_equip),
                            fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun GearDetailArtwork(resource: Int, title: String, zoomable: Boolean) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // The image layer follows every gesture frame; controls only change at the limits.
    val canZoomOut by remember { derivedStateOf { scale > 1f } }
    val canZoomIn by remember { derivedStateOf { scale < 5f } }

    fun bounded(position: Offset, zoom: Float): Offset {
        val maxX = viewport.width * (zoom - 1f) / 2f
        val maxY = viewport.height * (zoom - 1f) / 2f
        return Offset(position.x.coerceIn(-maxX, maxX), position.y.coerceIn(-maxY, maxY))
    }

    fun changeScale(next: Float) {
        val zoom = next.coerceIn(1f, 5f)
        offset = bounded(offset * (zoom / scale), zoom)
        scale = zoom
    }

    val transform = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        offset = bounded(offset * (next / scale) + pan, next)
        scale = next
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val imageWidth = maxWidth
            val imageHeight = (imageWidth * .95f).coerceIn(240.dp, 480.dp)
            val density = LocalDensity.current.density
            // Decode a readable map once, not a small preview stretched by the gesture layer.
            // Target up to 2400 px (and at most 5x the viewport), without requesting less
            // than the actual viewport when a dense display already exceeds that target.
            val decodeFactor = if (zoomable) {
                (2400f / (maxOf(imageWidth.value, imageHeight.value) * density))
                    .coerceIn(1f, 5f)
            } else 1f
            Box(
                Modifier.fillMaxWidth().height(imageHeight)
                    .clip(RoundedCornerShape(24.dp)).background(GearColors.Card)
                    .onSizeChanged {
                        viewport = it
                        offset = bounded(offset, scale)
                    }
                    .then(if (zoomable) Modifier
                        .semantics { stateDescription = "Масштаб ${(scale * 100).roundToInt()}%" }
                        .transformable(state = transform, canPan = { scale > 1f })
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { touch ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 3f
                                    offset = bounded(
                                        (Offset(viewport.width / 2f, viewport.height / 2f) - touch) * (scale - 1f),
                                        scale,
                                    )
                                }
                            })
                        } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                GameArtwork(
                    resource, title,
                    Modifier.requiredSize(imageWidth * decodeFactor, imageHeight * decodeFactor)
                        .graphicsLayer {
                            scaleX = scale / decodeFactor
                            scaleY = scale / decodeFactor
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }
        }
        if (zoomable) {
            Row(
                Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    onClick = { changeScale(scale / 1.5f) }, enabled = canZoomOut,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Уменьшить изображение" },
                    colors = ButtonDefaults.textButtonColors(contentColor = GearColors.Ink),
                ) { Text("−", fontSize = 24.sp) }
                TextButton(
                    onClick = { changeScale(1f) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Показать изображение целиком" },
                    colors = ButtonDefaults.textButtonColors(contentColor = GearColors.Ink),
                ) { Text("Целиком", fontFamily = Nunito) }
                TextButton(
                    onClick = { changeScale(scale * 1.5f) }, enabled = canZoomIn,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Увеличить изображение" },
                    colors = ButtonDefaults.textButtonColors(contentColor = GearColors.Ink),
                ) { Text("+", fontSize = 24.sp) }
            }
        }
    }
}
