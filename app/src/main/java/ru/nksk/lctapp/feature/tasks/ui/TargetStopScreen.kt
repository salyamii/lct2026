package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.feature.tasks.logic.TargetStopState

private const val MARKER_SIZE_DP = 26

/** Дело «Настрой телескоп»: пять раундов, останови бегунок в зелёной зоне ловушки. */
@Composable
fun TargetStopScreen(onBack: () -> Unit, onFinish: (Int) -> Unit) {
    var state by remember { mutableStateOf(TargetStopState.create()) }
    var paused by remember { mutableStateOf(false) }
    val marker = remember { Animatable(0f) }

    LaunchedEffect(paused, state.round, state.finished) {
        if (!paused && !state.finished) {
            while (true) {
                marker.animateTo(1f, tween(durationMillis = 1300, easing = LinearEasing))
                marker.animateTo(0f, tween(durationMillis = 1300, easing = LinearEasing))
            }
        }
    }
    LaunchedEffect(paused, state.round) {
        if (paused && !state.finished) {
            delay(900)
            state = state.next()
            paused = false
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) onFinish(state.reward)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            Image(
                painterResource(R.drawable.location_observatory),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            DeedHeader("Настрой телескоп", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f)) {
            Text(
                "Поймай сигнал звезды",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Раунд ${minOf(state.round + 1, TargetStopState.ROUNDS)} из ${TargetStopState.ROUNDS}")
                CoinChip("Награда · +${state.reward}")
            }
            Spacer(Modifier.height(18.dp))
            Track(
                zoneStart = state.zoneStart,
                markerPosition = marker.value,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                when (state.lastHit) {
                    true -> "Сигнал пойман! +${TargetStopState.REWARD_PER_HIT} монет"
                    false -> "Сигнал ушёл — попробуй ещё"
                    null -> if (state.finished) "Готово!" else "Тапни «Стоп», когда бегунок в зелёной зоне"
                },
                fontSize = 14.sp,
                fontFamily = Nunito,
                color = DeedColors.TextSoft,
            )
            Spacer(Modifier.height(18.dp))
            DeedButton("Стоп!", onClick = {
                if (!paused && !state.finished) {
                    paused = true
                    state = state.stop((marker.value * 100).toInt())
                }
            })
        }
    }

    if (state.finished) {
        DeedResultSheet(
            emoji = "🔭",
            title = "Телескоп настроен!",
            reward = state.reward,
            onAgain = {
                paused = false
                state = TargetStopState.create()
            },
            onHub = onBack,
        )
    }
}

@Composable
private fun Track(zoneStart: Int, markerPosition: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DeedColors.Board),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .width(maxWidth * (TargetStopState.ZONE_WIDTH / 100f))
                .height(40.dp)
                .offset(x = maxWidth * (zoneStart / 100f))
                .background(DeedColors.Lime),
        )
        Box(
            modifier = Modifier
                .size(MARKER_SIZE_DP.dp)
                .offset(x = (maxWidth - MARKER_SIZE_DP.dp) * markerPosition)
                .clip(CircleShape)
                .background(DeedColors.Chip),
        )
    }
}
