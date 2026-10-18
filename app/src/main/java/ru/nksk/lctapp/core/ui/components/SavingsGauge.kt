package ru.nksk.lctapp.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito

private data class SavingsCoinSlot(val column: Int, val level: Int, val order: Int)

private val SavingsStackHeights = listOf(22, 26, 18)
// Stable positions: all three stacks grow together, with the middle one slightly taller.
private val SavingsCoinSlots = SavingsStackHeights.flatMapIndexed { column, height ->
    (0 until height).map { level -> column to level }
}.sortedBy { (column, level) -> (level + 1f) / SavingsStackHeights[column] }
    .mapIndexed { order, (column, level) -> SavingsCoinSlot(column, level, order) }
    .sortedWith(compareBy<SavingsCoinSlot> { it.level }.thenBy { it.column })
private const val SavingsCoinCapacity = 66
private val SavingsFrameBands = floatArrayOf(0f, .058f, .106f, .848f, .909f, 1f)

/** Real savings only; native captions and three coin stacks share one illustrated housing. */
@Composable
internal fun SavingsGauge(
    saved: Long,
    total: Long,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val target = total.coerceAtLeast(0)
    val collected = saved.coerceIn(0, target)
    val progress = if (target > 0) (collected.toDouble() / target).toFloat() else 0f
    // This is illustrative fullness, not one sprite per currency unit.
    val coinCount = when {
        collected == 0L -> 0
        collected >= target -> SavingsCoinCapacity
        else -> (progress * SavingsCoinCapacity).roundToInt().coerceIn(1, SavingsCoinCapacity - 1)
    }
    val displayedCoins = animateFloatAsState(coinCount.toFloat(), tween(650), label = "Savings stacks")
    val description = contentDescription ?: "Цель: накоплено $collected из $target монет"
    val amount = "$collected из $target"
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val captionStyle = TextStyle(color = GameInk, fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 15.sp,
        textAlign = TextAlign.Center)

    BoxWithConstraints(modifier.width(104.dp).semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        this.contentDescription = description
    }) {
        val frameWidth = maxWidth
        val naturalHeight = maxWidth * 1.5f
        val captionWidth = maxWidth * .70f
        val textConstraints = Constraints(maxWidth = with(density) { captionWidth.roundToPx().coerceAtLeast(1) })
        val titleHeight = with(density) {
            textMeasurer.measure("Цель", captionStyle, constraints = textConstraints).size.height.toDp()
        } + 4.dp
        val amountHeight = with(density) {
            textMeasurer.measure(amount, captionStyle, constraints = textConstraints).size.height.toDp()
        } + 4.dp
        val topEdge = naturalHeight * .058f
        val bottomEdge = naturalHeight * .091f
        // An unusually short scene still exposes the amount; never place the bottom band outside its bounds.
        if (maxHeight < topEdge + titleHeight + amountHeight + bottomEdge) {
            if (maxHeight > 0.dp) GamePlaque(Modifier.width(maxWidth).height(maxHeight), light = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), fixedEdges = true) {
                Text(amount, Modifier.clearAndSetSemantics {}, style = captionStyle,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            return@BoxWithConstraints
        }
        val preferredHeight = naturalHeight * .891f + titleHeight + amountHeight
        val frameHeight = minOf(preferredHeight, maxHeight)
        val middleHeight = (frameHeight - topEdge - titleHeight - amountHeight - bottomEdge).coerceAtLeast(0.dp)
        val middleTop = topEdge + titleHeight
        val amountTop = middleTop + middleHeight
        // Stretch only the blank caption bands. The glass and decorative edges retain their proportions.
        val targetBands = listOf(0.dp, topEdge, middleTop, amountTop, amountTop + amountHeight, frameHeight)
        val glassTop = middleTop + middleHeight * ((.15f - .106f) / .742f)
        val glassHeight = middleHeight * ((.74f - .15f) / .742f)
        val glassWidth = maxWidth * .68f
        val coinWidth = glassWidth * .38f
        val coinHeight = coinWidth / 2f
        val frame = rememberGameArtworkLoad(R.drawable.menu_savings_frame_v2, DpSize(maxWidth, naturalHeight)).painter
        val coin = rememberGameArtworkLoad(R.drawable.menu_savings_coin_flat, DpSize(coinWidth, coinHeight)).painter

        Box(Modifier.width(maxWidth).height(frameHeight)) {
            Canvas(Modifier.size(glassWidth, glassHeight).offset(x = frameWidth * .16f, y = glassTop)
                .then(gameArtworkVisibility()).clearAndSetSemantics {}) {
                if (coin != null) {
                    val visibleCoins = displayedCoins.value
                    val width = size.width * .38f
                    val height = width / 2f
                    val bottom = (size.height - height).coerceAtLeast(0f)
                    val step = (bottom - height * .15f).coerceAtLeast(0f) / 25f
                    for (slot in SavingsCoinSlots) {
                        val appearance = (visibleCoins - slot.order).coerceIn(0f, 1f)
                        if (appearance > 0f) {
                            val x = size.width * (.20f + .30f * slot.column) - width / 2f
                            val y = bottom - slot.level * step - height * .12f * (1f - appearance)
                            translate(x, y) { with(coin) { draw(Size(width, height), alpha = appearance) } }
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize().then(gameArtworkVisibility()).clearAndSetSemantics {}) {
                if (frame != null) {
                    for (band in 0 until SavingsFrameBands.lastIndex) {
                        val top = targetBands[band].toPx()
                        val bottom = targetBands[band + 1].toPx()
                        val sourceTop = SavingsFrameBands[band]
                        val drawnHeight = (bottom - top) / (SavingsFrameBands[band + 1] - sourceTop)
                        if (drawnHeight > 0f) clipRect(top = top, bottom = bottom) {
                            translate(top = top - sourceTop * drawnHeight) {
                                with(frame) { draw(Size(size.width, drawnHeight)) }
                            }
                        }
                    }
                }
            }
            Box(Modifier.offset(x = frameWidth * .15f, y = topEdge).size(captionWidth, titleHeight),
                contentAlignment = Alignment.Center) {
                Text("Цель", Modifier.clearAndSetSemantics {}, style = captionStyle)
            }
            Box(Modifier.offset(x = frameWidth * .15f, y = amountTop).size(captionWidth, amountHeight),
                contentAlignment = Alignment.Center) {
                Text(amount, Modifier.clearAndSetSemantics {}, style = captionStyle)
            }
        }
    }
}
