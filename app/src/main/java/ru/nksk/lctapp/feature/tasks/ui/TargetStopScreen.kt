package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.TargetStopState

private const val MARKER_SIZE_DP = 26

/** Дело «Настрой телескоп»: пять раундов, останови бегунок в зелёной зоне ловушки. */
@Composable
fun TargetStopScreen(
    uiState: TargetStopUiState,
    onAction: (TargetStopAction) -> Unit,
    onBack: () -> Unit,
) {
    val state = uiState.game
    androidx.activity.compose.BackHandler(enabled = uiState.session.saving) { }
    if (!state.finished && MiniGameAccessGate(
        uiState.session, { onAction(TargetStopAction.Retry) }, onBack,
    )) return
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val markerPosition = if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        state.lastHit == null && !state.finished
    ) {
        key(state.round) {
            val transition = rememberInfiniteTransition(label = "telescope")
            val position by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1300, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "markerPosition",
            )
            position
        }
    } else {
        uiState.stoppedPosition ?: 0f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        val sceneHeight = if (maxHeight < 480.dp) 72.dp else 150.dp
        Column(Modifier.fillMaxSize()) {
            Box {
                Image(
                    painterResource(R.drawable.location_observatory),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(sceneHeight),
                    contentScale = ContentScale.Crop,
                )
                DeedHeader(stringResource(R.string.deeds_target_title), onBack = onBack)
            }
            DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.deeds_target_prompt),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Rubik,
                    color = DeedColors.Text,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DeedChip(stringResource(R.string.deeds_round, uiState.roundNumber, TargetStopState.ROUNDS))
                    CoinChip(stringResource(R.string.deeds_demo_reward, state.reward))
                }
                Spacer(Modifier.height(18.dp))
                Track(
                    zoneStart = state.zoneStart,
                    markerPosition = markerPosition,
                    modifier = Modifier.fillMaxWidth().testTag("telescope_track"),
                )
                Spacer(Modifier.height(8.dp))
                DeedButton(
                    text = stringResource(R.string.deeds_stop),
                    enabled = state.lastHit == null && !state.finished,
                    onClick = { onAction(TargetStopAction.Stop(markerPosition)) },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    when (state.lastHit) {
                        true -> stringResource(R.string.deeds_hit, TargetStopState.REWARD_PER_HIT)
                        false -> stringResource(R.string.deeds_miss)
                        null -> if (state.finished) stringResource(R.string.deeds_ready) else stringResource(R.string.deeds_target_hint)
                    },
                    fontSize = 14.sp,
                    fontFamily = Nunito,
                    color = DeedColors.TextSoft,
                )
                Spacer(Modifier.height(18.dp))

            }
        }
    }

    if (state.finished) {
        DeedResultSheet(
            emoji = "🔭",
            title = stringResource(R.string.deeds_target_complete),
            reward = state.reward,
            onAgain = { onAction(TargetStopAction.Restart) },
            onHub = onBack,
            session = uiState.session,
            onRetry = { onAction(TargetStopAction.Retry) },
        )
    }
}

@Composable
private fun Track(zoneStart: Int, markerPosition: Float, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "zone")
    val zoneGlow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "zoneGlow",
    )
    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(DeedColors.Board)
            .border(1.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Линейка: деления каждые 10%
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(9) { tick ->
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .height(if (tick == 4) 22.dp else 12.dp)
                        .background(Color.White.copy(alpha = if (tick == 4) 0.30f else 0.16f)),
                )
            }
        }
        // Зона-ловушка: свечение, скобы-кронштейны, лёгкая пульсация
        Box(
            modifier = Modifier
                .width(maxWidth * (TargetStopState.ZONE_WIDTH / 100f))
                .height(48.dp)
                .offset(x = maxWidth * (zoneStart / 100f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeedColors.Lime.copy(alpha = 0.85f * zoneGlow)),
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(DeedColors.Lime),
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .align(Alignment.CenterEnd)
                    .background(DeedColors.Lime),
            )
        }
        // Шестерёнка-бегунок
        Image(
            painter = painterResource(R.drawable.deed_gear_marker),
            contentDescription = null,
            modifier = Modifier
                .size(MARKER_SIZE_DP.dp)
                .offset(x = (maxWidth - MARKER_SIZE_DP.dp) * markerPosition)
                .graphicsLayer { rotationZ = markerPosition * 720f },
        )
    }
}
