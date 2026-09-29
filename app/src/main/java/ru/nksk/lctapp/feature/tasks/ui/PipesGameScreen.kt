package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.domain.minigame.PipesState

/** Тайл каната: рисунок, поворот и зеркалирование под направление тропинки. */
private data class RopeTile(val res: Int, val rotation: Float = 0f, val mirrored: Boolean = false)

/** Фон плиток под цвет каната, как в макете. */
private val TILE_COLORS = listOf(
    Color(0xFFE0805A),
    Color(0xFF6FA8DC),
    Color(0xFF9BBF6B),
)

/** Поворот конца каната наружу, прочь от парного конца. */
private fun outwardRotation(state: PipesState, cell: Int, color: Int): Float {
    val pair = state.endpoints.first { it.color == color }
    val other = if (cell == pair.first) pair.second else pair.first
    val dr = cell / PipesState.SIZE - other / PipesState.SIZE
    val dc = cell % PipesState.SIZE - other % PipesState.SIZE
    val towards = if (kotlin.math.abs(dr) >= kotlin.math.abs(dc)) {
        if (dr >= 0) 2 else 0
    } else {
        if (dc >= 0) 1 else 3
    }
    return towards * 90f
}

/** 0 - вверх, 1 - вправо, 2 - вниз, 3 - влево. */
private fun direction(from: Int, to: Int): Int = when {
    to == from - PipesState.SIZE -> 0
    to == from + 1 && to / PipesState.SIZE == from / PipesState.SIZE -> 1
    to == from + PipesState.SIZE -> 2
    else -> 3
}

/** Угол по паре «откуда пришёл → куда идёт»: канат входит сверху и уходит вправо - 0°. */
private fun cornerRotation(from: Int, outTo: Int): Int = when {
    from == 0 && outTo == 1 -> 0
    from == 1 && outTo == 2 -> 90
    from == 2 && outTo == 3 -> 180
    else -> 270
}

/**
 * Раскладывает канатные тайлы по ячейкам: концы пары - цветной канат,
 * повороты - уголок (при необходимости зеркальный), прямоходы - прямой канат.
 */
private fun ropeTiles(state: PipesState, activeColor: Int?): Map<Int, RopeTile> {
    val endRes = listOf(
        R.drawable.deed_game_pipes_end_red,
        R.drawable.deed_game_pipes_end_blue,
        R.drawable.deed_game_pipes_end_green,
    )
    val tiles = mutableMapOf<Int, RopeTile>()
    // Непроложенные концы смотрят наружу, прочь от своей пары, и не крутятся при нажатии.
    state.endpoints.forEach { endpoint ->
        if (endpoint.color !in state.paths) {
            val art = endRes[endpoint.color % endRes.size]
            tiles[endpoint.first] = RopeTile(art, outwardRotation(state, endpoint.first, endpoint.color))
            tiles[endpoint.second] = RopeTile(art, outwardRotation(state, endpoint.second, endpoint.color))
        }
    }
    fun addPath(path: List<Int>, color: Int, locked: Boolean) {
        if (path.isEmpty()) return
        path.forEachIndexed { index, cell ->
            val inFrom = when {
                index > 0 -> direction(cell, path[index - 1])
                path.size > 1 -> direction(cell, path[1])
                else -> null
            }
            val tile = when {
                // Начало тропинки - конец каната: до первого шага смотрит наружу,
                // после - поворачивается по направлению, выбранному игроком.
                index == 0 && !locked && path.size == 1 ->
                    RopeTile(endRes[color % endRes.size], outwardRotation(state, cell, color))
                index == 0 -> RopeTile(endRes[color % endRes.size], (inFrom ?: 0) * 90f)
                index == path.lastIndex && locked -> RopeTile(endRes[color % endRes.size], (inFrom ?: 0) * 90f)
                index == path.lastIndex -> {
                    // Голова недоведённой тропинки: прямой канат по последнему сегменту.
                    RopeTile(R.drawable.deed_game_pipes_straight, if ((inFrom ?: 0) % 2 == 1) 90f else 0f)
                }
                else -> {
                    val from = direction(cell, path[index - 1])
                    val outTo = direction(cell, path[index + 1])
                    if (from % 2 == outTo % 2) {
                        RopeTile(R.drawable.deed_game_pipes_straight, if (outTo % 2 == 1) 90f else 0f)
                    } else {
                        val mirrored = (from to outTo) in setOf(0 to 3, 3 to 2, 2 to 1, 1 to 0)
                        val angle = if (!mirrored) cornerRotation(from, outTo)
                        else when {
                            from == 0 && outTo == 3 -> 0
                            from == 3 && outTo == 2 -> 270
                            from == 2 && outTo == 1 -> 180
                            else -> 90
                        }
                        RopeTile(R.drawable.deed_game_pipes_corner, angle.toFloat(), mirrored)
                    }
                }
            }
            tiles[cell] = tile
        }
    }
    state.endpoints.forEach { endpoint -> state.paths[endpoint.color]?.let { addPath(it, endpoint.color, locked = true) } }
    state.activePath.takeIf { it.isNotEmpty() }?.let { addPath(it, activeColor ?: 0, locked = false) }
    return tiles
}

/** Shared connect-the-ends board for offered deeds. */
@Composable
fun PipesGameScreen(
    uiState: PipesGameUiState,
    onAction: (PipesGameAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    val activeColor = state.activeColor
    val tiles = remember(state, activeColor) { ropeTiles(state, activeColor) }
    Column(
        modifier = Modifier.fillMaxSize().background(DeedColors.Scene),
    ) {
        Box {
            GameArtwork(deed?.sceneRes ?: R.drawable.location_workshop,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp).padding(4.dp)) }
            DeedHeader(deed?.title ?: "Свяжи концы", onBack = onBack)
        }
        DeedGameSheet(deed, DeedRewardPreview.fromPipes(state), modifier = Modifier.weight(1f)) {
            Text(
                deed?.instructions ?: "Соедини концы одного цвета непрерывной линией, не пересекая чужие!",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip(when {
                    state.paths.isNotEmpty() -> "Пары: ${state.paths.size} / ${state.endpoints.size}"
                    activeColor != null -> "Цвет ${activeColor + 1}"
                    else -> "Выбери конец"
                })
                if (deed == null) {
                    CoinChip("Награда: 10")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until PipesState.SIZE) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0 until PipesState.SIZE) {
                            val cell = row * PipesState.SIZE + col
                            val endpoint = state.endpoints.firstOrNull { it.first == cell || it.second == cell }
                            val pathColor = state.endpoints.firstOrNull { endpoint ->
                                state.paths[endpoint.color]?.contains(cell) == true
                            }
                            val activeCell = cell in state.activePath
                            val tileColor = when {
                                pathColor != null -> TILE_COLORS[pathColor.color]
                                activeCell -> TILE_COLORS[activeColor ?: 0].copy(alpha = 0.45f)
                                endpoint != null -> TILE_COLORS[endpoint.color]
                                else -> DeedColors.CreamSoft
                            }
                            val shape = RoundedCornerShape(10.dp)
                            val description = when {
                                endpoint != null -> "Конец цвета ${endpoint.color + 1}, ячейка ${cell + 1}"
                                activeCell -> "Тропинка цвета ${(activeColor ?: 0) + 1}, ячейка ${cell + 1}"
                                pathColor != null -> "Линия цвета ${pathColor.color + 1}, ячейка ${cell + 1}"
                                else -> "Свободная ячейка ${cell + 1}"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .semantics { contentDescription = description }
                                    .clip(shape)
                                    .background(tileColor)
                                    .clickable(enabled = !state.won && deed?.canPlay != false) {
                                        onAction(PipesGameAction.Press(cell))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                tiles[cell]?.let { tile ->
                                    Image(
                                        painter = painterResource(tile.res),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                alpha = if (activeCell) 0.6f else 1f
                                                rotationZ = tile.rotation
                                                scaleX = if (tile.mirrored) -1f else 1f
                                            },
                                        alignment = Alignment.Center,
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (state.activeColor != null && !state.won) {
                DeedButtonSoft("Сбросить тропинку", { onAction(PipesGameAction.Release) })
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    if (state.won && deed == null) {
        DeedResultSheet(
            emoji = "🪢",
            title = "Все концы связаны!",
            reward = 10,
            onAgain = { onAction(PipesGameAction.Restart) },
            onHub = onBack,
        )
    }
}
