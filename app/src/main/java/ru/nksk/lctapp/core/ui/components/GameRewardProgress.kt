package ru.nksk.lctapp.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito

/** A read-only reward gauge. Animation invalidates drawing, not the game board's layout. */
@Composable
internal fun GameRewardProgress(remaining: Long, maximum: Long, modifier: Modifier = Modifier) {
    require(maximum > 0 && remaining in 0..maximum)
    val fraction = remaining.toFloat() / maximum.toFloat()
    val animatedFraction = animateFloatAsState(fraction, tween(900), label = "remainingReward")
    val coin = rememberGameArtworkLoad(R.drawable.menu_coin, DpSize(24.dp, 24.dp)).painter
    val description = stringResource(R.string.deeds_reward_available_description, remaining, maximum)
    Column(modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = description
        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
    }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.deeds_reward_available), color = GameInk,
                fontFamily = Nunito, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("$remaining / $maximum", color = GameInk,
                fontFamily = Nunito, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val gap = 4.dp.toPx()
            val capacity = ((size.width + gap) / (24.dp.toPx() + gap)).toInt().coerceAtLeast(1)
            val count = minOf(maximum, 12L, capacity.toLong()).toInt()
            val coinSize = minOf(24.dp.toPx(), (size.width - gap * (count - 1)) / count)
            val filled = animatedFraction.value * count
            val painter = coin ?: return@Canvas
            repeat(count) { index ->
                translate(left = index * (coinSize + gap), top = (size.height - coinSize) / 2) {
                    with(painter) { draw(Size(coinSize, coinSize), alpha = .18f) }
                    val fill = (filled - index).coerceIn(0f, 1f)
                    clipRect(right = coinSize * fill) {
                        with(painter) { draw(Size(coinSize, coinSize)) }
                    }
                }
            }
        }
    }
}
