package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.domain.minigame.PackingState

/** Дело «Лишняя покупка»: собери набор для цели, убрав ненужный предмет. */
@Composable
fun PackingGameScreen(
    uiState: PackingUiState,
    onAction: (PackingAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    FinGameBoard(
        deed = deed,
        title = stringResource(R.string.deeds_packing_title),
        sceneRes = R.drawable.location_workshop,
        preview = DeedRewardPreview.fromPacking(state),
        onBack = onBack,
    ) {
        Text(
            deed?.instructions ?: stringResource(R.string.deeds_packing_hint),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedChip(stringResource(R.string.deeds_question,
            minOf(state.current + 1, PackingState.ROUNDS), PackingState.ROUNDS))
        Spacer(Modifier.height(14.dp))
        if (!state.finished || deed != null) {
            val round = state.rounds[minOf(state.current, state.rounds.lastIndex)]
            val locked = state.lastCorrect != null || deed?.canPlay == false
            Text(stringResource(R.string.deeds_packing_goal, round.goal),
                fontSize = 16.sp, lineHeight = 22.sp, fontFamily = Nunito, color = DeedColors.Text)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                round.items.forEachIndexed { index, item ->
                    ItemCard(item, removed = state.lastCorrect != null && index == round.oddIndex,
                        enabled = !locked, onClick = { onAction(PackingAction.Remove(index)) },
                        modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(14.dp))
            FinFeedback(
                text = when (state.lastCorrect) {
                    true -> stringResource(R.string.deeds_packing_correct)
                    false -> stringResource(R.string.deeds_packing_wrong)
                    null -> if (state.finished) stringResource(R.string.deeds_ready)
                        else stringResource(R.string.deeds_packing_hint)
                },
                buttonText = stringResource(if (minOf(state.current, state.rounds.lastIndex) == state.rounds.lastIndex)
                    R.string.deeds_price_finish else R.string.deeds_price_next),
                buttonEnabled = !state.finished && state.lastCorrect != null && deed?.canPlay != false,
                onNext = { onAction(PackingAction.Next(state.current)) },
            )
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(emoji = "🎒", title = stringResource(R.string.deeds_packing_complete),
            reward = state.correctRounds * 2, onAgain = { onAction(PackingAction.Restart) }, onHub = onBack)
    }
}

@Composable
private fun ItemCard(name: String, removed: Boolean, enabled: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (removed) DeedColors.CreamSoft else DeedColors.CreamCard)
            .then(if (removed) Modifier.border(3.dp, DeedColors.Lime, shape)
                else Modifier.border(1.dp, DeedColors.Border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { selected = removed }
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 13.sp, lineHeight = 17.sp, fontFamily = Nunito, color = DeedColors.Text)
    }
}
