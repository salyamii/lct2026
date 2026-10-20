package ru.nksk.lctapp.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R

/** Only the empty middle stretches; the decorative ends keep their shape. */
@Composable
internal fun GamePlaque(
    modifier: Modifier = Modifier,
    light: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    @DrawableRes artworkRes: Int? = null,
    fixedEdges: Boolean = false,
    fillVisibleHeight: Boolean = false,
    colorFilter: ColorFilter? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resource = artworkRes ?: if (light) R.drawable.menu_action_plaque_v2 else R.drawable.menu_hud_plaque_v2
    // Fixed corners are drawn at most 64dp high; larger text only stretches the empty middle.
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val decodeSize = if (fixedEdges) DpSize(192.dp, 64.dp)
        else DpSize(240.dp * textScale, 80.dp * textScale)
    val painter = rememberGameArtworkLoad(resource, decodeSize, participateInScene = false).painter
    val opacity = animateFloatAsState(if (painter != null) 1f else 0f,
        tween(180), label = "plaque-artwork-reveal")
    // Native labels and controls must not wait for unrelated scene artwork to finish loading.
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().graphicsLayer { alpha = opacity.value }) {
            if (painter != null && size.width > 0 && size.height > 0) {
                val ratio = painter.intrinsicSize.let { if (it.height > 0) it.width / it.height else 3f }
                if (fixedEdges) {
                    // A multiline action grows through its empty middle; corners do not grow into its label.
                    val referenceHeight = minOf(size.height, 64.dp.toPx())
                    val edgeX = minOf(referenceHeight * ratio * .16f, size.width / 2f)
                    val edgeY = minOf(referenceHeight * .26f, size.height / 2f)
                    val sourceX = floatArrayOf(0f, .16f, .84f, 1f)
                    val sourceY = floatArrayOf(0f, .26f, .74f, 1f)
                    val targetX = floatArrayOf(0f, edgeX, size.width - edgeX, size.width)
                    // This frame's visible wood occupies y=71..632 of its 724px canvas.
                    // Keep the full source and corner scale, but let its transparent margins
                    // extend outside an action's bounds instead of inflating the visible gap.
                    val compensateMargins = fillVisibleHeight && resource == R.drawable.menu_action_plaque_v2
                    val topInset = if (compensateMargins) referenceHeight * (71f / 724f) else 0f
                    val bottomInset = if (compensateMargins) referenceHeight * (92f / 724f) else 0f
                    val targetY = floatArrayOf(-topInset, edgeY - topInset,
                        size.height - edgeY + bottomInset, size.height + bottomInset)
                    for (row in 0..2) for (column in 0..2) {
                        val width = targetX[column + 1] - targetX[column]
                        val height = targetY[row + 1] - targetY[row]
                        if (width > 0f && height > 0f) {
                            val drawnWidth = width / (sourceX[column + 1] - sourceX[column])
                            val drawnHeight = height / (sourceY[row + 1] - sourceY[row])
                            clipRect(targetX[column], targetY[row], targetX[column + 1], targetY[row + 1]) {
                                translate(targetX[column] - sourceX[column] * drawnWidth,
                                    targetY[row] - sourceY[row] * drawnHeight) {
                                    with(painter) { draw(Size(drawnWidth, drawnHeight), colorFilter = colorFilter) }
                                }
                            }
                        }
                    }
                    return@Canvas
                }
                val naturalWidth = size.height * ratio
                val edge = minOf(naturalWidth * .16f, size.width / 2f)
                clipRect(right = edge) {
                    with(painter) { draw(Size(naturalWidth, size.height), colorFilter = colorFilter) }
                }
                clipRect(left = size.width - edge) {
                    translate(left = size.width - naturalWidth) {
                        with(painter) { draw(Size(naturalWidth, size.height), colorFilter = colorFilter) }
                    }
                }
                if (size.width > edge * 2f) {
                    val middleWidth = (size.width - edge * 2f) / .68f
                    clipRect(left = edge, right = size.width - edge) {
                        translate(left = edge - middleWidth * .16f) {
                            with(painter) { draw(Size(middleWidth, size.height), colorFilter = colorFilter) }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(contentPadding), contentAlignment = Alignment.Center, content = content)
    }
}
