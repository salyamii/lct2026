package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.domain.minigame.NeedWantState

/** Дело «Нужда или желание?»: отнеси каждый предмет к необходимому или к желаниям. */
@Composable
fun NeedWantGameScreen(
    uiState: NeedWantUiState,
    onAction: (NeedWantAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    FinGameBoard(
        deed = deed,
        title = stringResource(R.string.deeds_needwant_title),
        sceneRes = R.drawable.location_observatory,
        preview = DeedRewardPreview.fromNeedWant(state),
        onBack = onBack,
    ) {
        Text(
            deed?.instructions ?: stringResource(R.string.deeds_needwant_hint),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedChip(stringResource(R.string.deeds_question,
            minOf(state.current + 1, NeedWantState.ITEM_COUNT), NeedWantState.ITEM_COUNT))
        Spacer(Modifier.height(14.dp))
        if (!state.finished || deed != null) {
            val item = state.items[minOf(state.current, state.items.lastIndex)]
            val locked = state.lastCorrect != null || deed?.canPlay == false
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeedColors.CreamCard)
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.name, textAlign = TextAlign.Center, fontSize = 22.sp, lineHeight = 28.sp,
                    fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeedButtonSoft(stringResource(R.string.deeds_needwant_want),
                    onClick = { onAction(NeedWantAction.Classify(saidNeed = false, itemIndex = state.current)) },
                    modifier = Modifier.weight(1f), enabled = !locked)
                DeedButton(stringResource(R.string.deeds_needwant_need),
                    onClick = { onAction(NeedWantAction.Classify(saidNeed = true, itemIndex = state.current)) },
                    modifier = Modifier.weight(1f), enabled = !locked)
            }
            Spacer(Modifier.height(14.dp))
            FinFeedback(
                text = when (state.lastCorrect) {
                    true -> stringResource(R.string.deeds_correct)
                    false -> stringResource(R.string.deeds_incorrect)
                    null -> if (state.finished) stringResource(R.string.deeds_ready)
                        else stringResource(R.string.deeds_needwant_hint)
                },
                buttonText = stringResource(if (minOf(state.current, state.items.lastIndex) == state.items.lastIndex)
                    R.string.deeds_price_finish else R.string.deeds_price_next),
                buttonEnabled = !state.finished && state.lastCorrect != null && deed?.canPlay != false,
                onNext = { onAction(NeedWantAction.Next(state.current)) },
            )
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(emoji = "🎒", title = stringResource(R.string.deeds_needwant_complete),
            reward = state.correctAnswers * 2, onAgain = { onAction(NeedWantAction.Restart) }, onHub = onBack)
    }
}
