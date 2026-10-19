package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.GameHomeButton
import ru.nksk.lctapp.core.ui.components.InlineSavingsTransfer
import ru.nksk.lctapp.core.ui.components.SavingsGauge
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Nunito
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeedGameHost(state: DeedGameUiState, onRetry: () -> Unit, onBack: () -> Unit,
    onHome: () -> Unit = onBack,
    onSkipGame: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
    onSavingsInput: (String) -> Unit = {},
    onConfirmSavings: () -> Unit = {},
    onCancelSavings: () -> Unit = {},
    content: @Composable () -> Unit) {
    if (state.reward != null) {
        DeedRewardResult(state, onRetry, onBack, onHome, onOpenSavings, onSavingsInput, onConfirmSavings, onCancelSavings)
    } else if (state.presentation == null) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.loading) GameLoadingIndicator()
                    state.message?.let { Text(it) }
                    if (state.canRetry) Button(onRetry, enabled = !state.busy) { Text("Повторить") }
                }
                GameHomeButton(onHome, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp),
                    enabled = state.canReturnHome)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().then(if (state.demoMode) Modifier.statusBarsPadding() else Modifier)) {
            if (state.demoMode) Surface(color = DeedColors.Cream) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Режим бога", style = MaterialTheme.typography.labelMedium)
                    TextButton(onSkipGame, enabled = state.canSkipGame && !state.busy) { Text("Пропустить игру") }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
                if (state.busy || state.message != null) Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = DeedColors.Cream,
                ) {
                    Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                        if (state.busy) GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
                        state.message?.let { Text(it, color = DeedColors.Text) }
                        if (state.canRetry) Button(onRetry, enabled = !state.busy) { Text("Повторить") }
                    }
                }
            }
        }
    }
}

/** Completion replaces the board inside its existing route; it is never a second dialog. */
@Composable
private fun DeedRewardResult(state: DeedGameUiState, onRetry: () -> Unit, onContinue: () -> Unit, onHome: () -> Unit,
    onOpenSavings: () -> Unit, onSavingsInput: (String) -> Unit,
    onConfirmSavings: () -> Unit, onCancelSavings: () -> Unit) {
    val reward = checkNotNull(state.reward)
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        DeedBackdrop(reward.sceneRes, modifier = Modifier.fillMaxSize())
        GameHomeButton(onHome, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(16.dp),
            enabled = state.canReturnHome)
        // The jar belongs to the full screen, not to the form's remaining space.
        // Its position and size depend only on the viewport before keyboard insets.
        val jarHeight = (maxHeight * .26f).coerceAtMost(320.dp)
        val jarWidth = minOf(192.dp, maxWidth * .5f, jarHeight / 1.7f)
        val panelMaxHeight = ((maxHeight - jarHeight) / 2 - 12.dp).coerceAtLeast(0.dp)
        Box(Modifier.size(jarWidth, jarHeight).align(Alignment.Center), contentAlignment = Alignment.Center) {
            SavingsGauge(reward.savingsBalance, reward.goalPrice, Modifier.width(jarWidth),
                contentDescription = "На цель «${reward.goalTitle}»: накоплено ${reward.savingsBalance} из ${reward.goalPrice} монет")
        }
        // Only the form moves above the keyboard and scrolls. Its normal bounds
        // leave the centred jar visible, without stretching the cream background.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Box(Modifier.fillMaxWidth().heightIn(max = panelMaxHeight)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(DeedColors.Cream).verticalScroll(scroll)) {
                DeedRewardActions(state, onRetry, onContinue, onOpenSavings, onSavingsInput,
                    onConfirmSavings, onCancelSavings)
            }
        }
    }
}

@Composable
private fun DeedRewardActions(state: DeedGameUiState, onRetry: () -> Unit, onContinue: () -> Unit,
    onOpenSavings: () -> Unit, onSavingsInput: (String) -> Unit,
    onConfirmSavings: () -> Unit, onCancelSavings: () -> Unit) {
    val reward = checkNotNull(state.reward)
    DeedSheet {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Получено ${coinAmount(reward.earned)}", Modifier.weight(1f), fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = DeedColors.Text)
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (state.busy) GameLoadingIndicator(size = 28.dp, delayMillis = 250)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("На цель: ${reward.goalTitle}", fontFamily = Nunito, fontSize = 15.sp,
            color = DeedColors.Text)
        Spacer(Modifier.height(12.dp))
        if (reward.deposited > 0) {
            Text("В копилке +${reward.deposited}", fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DeedColors.Text)
            Spacer(Modifier.height(12.dp))
        }
        if (state.canRetry) {
            GameActionButton("Повторить", onRetry, Modifier.fillMaxWidth(), interactionBlocked = state.busy)
        } else if (reward.editing) {
            InlineSavingsTransfer(reward.input, reward.maximum, reward.reserveBalance, reward.savingsBalance,
                enabled = true, onInputChange = onSavingsInput,
                onConfirm = onConfirmSavings, onCancel = onCancelSavings, interactionBlocked = state.busy)
        } else {
            if (reward.maximum > 0 && reward.deposited == 0L) {
                GameActionButton("Отложить на цель", onOpenSavings, Modifier.fillMaxWidth(), interactionBlocked = state.busy)
                Spacer(Modifier.height(8.dp))
            }
            GameActionButton("Продолжить", onContinue, Modifier.fillMaxWidth(), interactionBlocked = state.busy,
                style = GameActionStyle.SECONDARY)
        }
        state.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DeedColors.Text, fontFamily = Nunito)
        }
    }
}
