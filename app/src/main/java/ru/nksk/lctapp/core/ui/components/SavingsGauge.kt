package ru.nksk.lctapp.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import ru.nksk.lctapp.R

/** Real savings only. The full frame canvas and the hollow window share fixed coordinates. */
@Composable
internal fun SavingsGauge(
    saved: Long,
    total: Long,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val progress = if (total > 0) (saved.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else 0f
    val displayedProgress by animateFloatAsState(progress, tween(650), label = "Savings fill")
    val description = contentDescription
    BoxWithConstraints(modifier.width(96.dp).aspectRatio(2f / 3f).then(gameArtworkVisibility()).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        description?.let { this.contentDescription = it }
    }) {
        val innerWidth = maxWidth * .45f
        val innerHeight = maxHeight * .69f
        val coinSize = innerWidth * .58f
        val coin = rememberGameArtworkLoad(R.drawable.menu_coin, DpSize(coinSize, coinSize)).painter
        Canvas(Modifier.offset(x = maxWidth * .27f, y = maxHeight * .11f)
            .size(innerWidth, innerHeight).clip(RoundedCornerShape(4.dp))) {
            clipRect(top = size.height * (1f - displayedProgress)) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFE8BD58), Color(0xFFA96A17))))
                if (coin != null) {
                    val diameter = size.width * .58f
                    val step = diameter * .58f
                    val rows = ceil(size.height / step).toInt() + 1
                    repeat(rows) { row ->
                        repeat(2) { column ->
                            val x = if (column == 0) -diameter * .08f else size.width - diameter * .92f
                            val y = size.height - diameter - row * step + if (column == row % 2) step * .3f else 0f
                            translate(x, y) { with(coin) { draw(Size(diameter, diameter)) } }
                        }
                    }
                }
            }
        }
        GameArtwork(R.drawable.menu_savings_gauge_frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
