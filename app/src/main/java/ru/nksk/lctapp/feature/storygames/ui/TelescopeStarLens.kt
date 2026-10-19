package ru.nksk.lctapp.feature.storygames.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeAssembly

private data class TelescopeConstellation(
    val stars: List<Offset>,
    val edges: List<Pair<Int, Int>>,
)

// Drawn sky patterns for the story, not an astronomical map. Variant zero keeps
// the original sky for an already started game saved before variants existed.
private val TelescopeConstellations = listOf(
    TelescopeConstellation(
        stars = listOf(Offset(-.52f, -.30f), Offset(-.22f, -.48f), Offset(.02f, -.15f),
            Offset(.38f, -.22f), Offset(.50f, .22f), Offset(.15f, .48f), Offset(-.25f, .23f)),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6),
    ),
    // A kite and its curving tail.
    TelescopeConstellation(
        stars = listOf(Offset(-.12f, -.73f), Offset(.28f, -.32f), Offset(.04f, .12f),
            Offset(-.48f, -.28f), Offset(.18f, .36f), Offset(.02f, .58f), Offset(.22f, .73f)),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 2 to 4, 4 to 5, 5 to 6),
    ),
    // A boat with two sails.
    TelescopeConstellation(
        stars = listOf(Offset(-.67f, .26f), Offset(-.40f, .55f), Offset(.38f, .55f),
            Offset(.65f, .26f), Offset(-.04f, .26f), Offset(-.04f, -.67f),
            Offset(.50f, .07f), Offset(-.47f, .07f)),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 4, 5 to 7, 7 to 4),
    ),
    // A lantern with a handle and a small light in its middle.
    TelescopeConstellation(
        stars = listOf(Offset(-.30f, -.37f), Offset(.30f, -.37f), Offset(.43f, .32f),
            Offset(.25f, .57f), Offset(-.25f, .57f), Offset(-.43f, .32f),
            Offset(-.18f, -.65f), Offset(.18f, -.65f), Offset(.03f, .06f)),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 0,
            0 to 6, 6 to 7, 7 to 1, 5 to 8, 8 to 2),
    ),
    // A key, tilted across the lens.
    TelescopeConstellation(
        stars = listOf(Offset(-.61f, -.24f), Offset(-.40f, -.52f), Offset(-.10f, -.43f),
            Offset(.02f, -.16f), Offset(-.22f, .04f), Offset(.51f, .48f), Offset(.67f, .29f),
            Offset(.28f, .32f), Offset(.44f, .12f)),
        edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 0, 4 to 7, 7 to 5, 5 to 6, 7 to 8),
    ),
)

@Composable
internal fun TelescopeStarLens(game: TelescopeAssembly, modifier: Modifier = Modifier) {
    val constellation = TelescopeConstellations.getOrElse(game.skyVariant) { TelescopeConstellations.first() }
    Canvas(modifier.semantics {
        contentDescription = "Звёздное небо в телескопе"
        stateDescription = if (game.inFocus) "Звёзды видны чётко" else "Звёзды размыты"
    }) {
        val gold = Color(0xFFE8BC68)
        val starlight = Color(0xFFFFEDC9)
        val radius = size.minDimension * .44f
        drawCircle(Color(0xFF39251A), radius + 12.dp.toPx(), center)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF20395C), Color(0xFF070F20)), center, radius), radius, center)
        val lens = Path().apply { addOval(Rect(center, radius)) }
        clipPath(lens) {
            val points = constellation.stars.map { center + it * radius }
            if (game.completed) constellation.edges.forEach { (from, to) ->
                drawLine(gold.copy(alpha = .55f), points[from], points[to], 1.5.dp.toPx(), StrokeCap.Round)
            }
            val sharpness = game.sharpness
            val halo = (4 + (1 - sharpness) * 28).dp.toPx()
            val coreAlpha = ((sharpness - .25f) / .75f).coerceIn(0f, 1f)
            points.forEachIndexed { index, point ->
                drawCircle(Brush.radialGradient(listOf(starlight.copy(alpha = .7f), Color.Transparent), point, halo), halo, point)
                // A sharp centre emerges as the halo contracts; at the wrong focus the
                // stars are soft patches instead of permanent crisp dots under a glow.
                val starSize = (if (index % 3 == 0) 2.8f else 2.1f).dp.toPx()
                drawCircle(starlight.copy(alpha = coreAlpha), starSize, point)
                if (game.inFocus && index % 2 == 0) {
                    val shine = starlight.copy(alpha = coreAlpha)
                    val ray = 5.dp.toPx()
                    drawLine(shine, point - Offset(ray, 0f), point + Offset(ray, 0f), 1.dp.toPx(), StrokeCap.Round)
                    drawLine(shine, point - Offset(0f, ray), point + Offset(0f, ray), 1.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        drawCircle(Brush.linearGradient(listOf(Color(0xFF8C5527), gold, Color(0xFF9F6A31))),
            radius + 5.dp.toPx(), center, style = Stroke(8.dp.toPx()))
    }
}
