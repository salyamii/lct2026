package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.InlineSavingsTransfer
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Nunito
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DeedGameHost(state: DeedGameUiState, onRetry: () -> Unit, onBack: () -> Unit,
    onSkipGame: () -> Unit = {},
    onOpenSavings: () -> Unit = {},
    onSavingsInput: (String) -> Unit = {},
    onConfirmSavings: () -> Unit = {},
    onCancelSavings: () -> Unit = {},
    content: @Composable () -> Unit) {
    if (state.reward != null) {
        DeedRewardResult(state, onRetry, onBack, onOpenSavings, onSavingsInput, onConfirmSavings, onCancelSavings)
    } else if (state.presentation == null) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.loading) GameLoadingIndicator()
                state.message?.let { Text(it) }
                if (state.canRetry) Button(onRetry, enabled = !state.busy) { Text("Повторить") }
                TextButton(onBack) { Text("В главное меню") }
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
private fun DeedRewardResult(state: DeedGameUiState, onRetry: () -> Unit, onContinue: () -> Unit,
    onOpenSavings: () -> Unit, onSavingsInput: (String) -> Unit,
    onConfirmSavings: () -> Unit, onCancelSavings: () -> Unit) {
    val reward = checkNotNull(state.reward)
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val panelHeight = maxHeight * .85f
        GameArtwork(reward.sceneRes,
            contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        DeedSheet(Modifier.align(Alignment.BottomCenter).heightIn(max = panelHeight)
            .verticalScroll(rememberScrollState())) {
            Text("Получено ${coinAmount(reward.earned)}", fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = DeedColors.Text)
            Spacer(Modifier.height(8.dp))
            Text("На цель: ${reward.goalTitle}", fontFamily = Nunito, fontSize = 15.sp,
                color = DeedColors.Text)
            Spacer(Modifier.height(12.dp))
            if (reward.deposited > 0) {
                Text("В копилке +${reward.deposited}", fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = DeedColors.Text)
                Spacer(Modifier.height(12.dp))
            }
            state.message?.let {
                Text(it, color = DeedColors.Text, fontFamily = Nunito)
                Spacer(Modifier.height(8.dp))
            }
            if (state.canRetry) {
                GameActionButton("Повторить", onRetry, Modifier.fillMaxWidth(), enabled = !state.busy)
            } else if (reward.editing) {
                InlineSavingsTransfer(reward.input, reward.maximum, reward.reserveBalance, reward.savingsBalance,
                    enabled = !state.busy, onInputChange = onSavingsInput,
                    onConfirm = onConfirmSavings, onCancel = onCancelSavings)
            } else {
                if (reward.maximum > 0 && reward.deposited == 0L) {
                    GameActionButton("Отложить на цель", onOpenSavings, Modifier.fillMaxWidth(), enabled = !state.busy)
                    Spacer(Modifier.height(8.dp))
                }
                GameActionButton("Продолжить", onContinue, Modifier.fillMaxWidth(), enabled = !state.busy,
                    style = GameActionStyle.SECONDARY)
            }
            if (state.busy) GameLoadingIndicator(Modifier.fillMaxWidth(), size = 40.dp)
        }
    }
}
