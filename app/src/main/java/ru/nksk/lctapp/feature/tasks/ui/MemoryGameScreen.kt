package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.feature.tasks.logic.MemoryState

private val PLATE_FACES = listOf("🌙", "⭐", "🪐", "✨", "🌠", "🔭", "☄️", "🌌")

/** Дело «Звёздные пласты»: сетка 4×4, собери пары созвездий — награда фиксированная. */
@Composable
fun MemoryGameScreen(onBack: () -> Unit, onFinish: (Int) -> Unit) {
    var state by remember { mutableStateOf(MemoryState.deal()) }

    LaunchedEffect(state.moves) {
        if (state.pending != null) {
            delay(700)
            state = state.resolvePending()
        }
    }
    LaunchedEffect(state.won) {
        if (state.won) onFinish(MemoryState.REWARD)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            Image(
                painterResource(R.drawable.location_hill),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            DeedHeader("Звёздные пласты", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f)) {
            Text(
                "Найди пары созвездий",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Ходы: ${state.moves}")
                CoinChip("Награда · +${MemoryState.REWARD}")
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.faces.chunked(4).forEachIndexed { rowIndex, rowFaces ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowFaces.forEachIndexed { columnIndex, face ->
                            val index = rowIndex * 4 + columnIndex
                            StarPlateView(
                                face = PLATE_FACES[face],
                                revealed = index in state.faceUp || index in state.matched,
                                matched = index in state.matched,
                                enabled = state.pending == null && !state.won,
                                onClick = {
                                    val resolved = if (state.pending != null) state.resolvePending() else state
                                    state = resolved.tap(index)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (state.won) {
        DeedResultSheet(
            emoji = "🌌",
            title = "Все созвездия на местах!",
            reward = MemoryState.REWARD,
            onAgain = { state = MemoryState.deal() },
            onHub = onBack,
        )
    }
}

@Composable
private fun StarPlateView(
    face: String,
    revealed: Boolean,
    matched: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        matched -> DeedColors.Lime.copy(alpha = 0.30f)
        revealed -> DeedColors.CreamCard
        else -> DeedColors.Board
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .then(if (revealed && !matched) Modifier.border(1.5.dp, DeedColors.Border, shape) else Modifier)
            .then(if (enabled && !revealed) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (revealed) {
            Text(face, fontSize = 26.sp)
        } else {
            Text(
                "✦",
                fontSize = 18.sp,
                color = DeedColors.Lime.copy(alpha = 0.8f),
            )
        }
    }
}
