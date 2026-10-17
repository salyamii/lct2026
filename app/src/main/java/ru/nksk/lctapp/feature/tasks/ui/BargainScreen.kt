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
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.BargainOffer
import ru.nksk.lctapp.domain.minigame.BargainState
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview

/** Дело «Выгодно или нет?»: два предложения, найди меньшую цену за штуку. */
@Composable
fun BargainGameScreen(
    uiState: BargainUiState,
    onAction: (BargainAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    FinGameBoard(
        deed = deed,
        title = stringResource(R.string.deeds_bargain_title),
        sceneRes = R.drawable.location_fair,
        preview = DeedRewardPreview.fromBargain(state),
        onBack = onBack,
    ) {
        Text(
            deed?.instructions ?: stringResource(R.string.deeds_bargain_hint),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedChip(stringResource(R.string.deeds_question,
            minOf(state.current + 1, BargainState.ROUNDS), BargainState.ROUNDS))
        Spacer(Modifier.height(14.dp))
        // Connected work returns to the menu after saving. Keep the last pair
        // visible during that write instead of collapsing the whole game board.
        if (!state.finished || deed != null) {
            val displayed = state.questions[minOf(state.current, state.questions.lastIndex)]
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OfferCard(
                    offer = displayed.left,
                    enabled = !state.finished && state.lastCorrect == null && deed?.canPlay != false,
                    showAsAnswer = if (state.finished) displayed.leftIsBetter else uiState.leftIsAnswer,
                    onClick = { onAction(BargainAction.Answer(pickedLeft = true, questionIndex = state.current)) },
                    modifier = Modifier.weight(1f),
                )
                OfferCard(
                    offer = displayed.right,
                    enabled = !state.finished && state.lastCorrect == null && deed?.canPlay != false,
                    showAsAnswer = if (state.finished) !displayed.leftIsBetter else uiState.rightIsAnswer,
                    onClick = { onAction(BargainAction.Answer(pickedLeft = false, questionIndex = state.current)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            FinFeedback(
                text = when (state.lastCorrect) {
                    true -> stringResource(R.string.deeds_correct)
                    false -> stringResource(R.string.deeds_incorrect)
                    null -> if (state.finished) stringResource(R.string.deeds_ready)
                        else stringResource(R.string.deeds_bargain_hint)
                },
                buttonText = stringResource(if (minOf(state.current, state.questions.lastIndex) == state.questions.lastIndex)
                    R.string.deeds_price_finish else R.string.deeds_price_next),
                buttonEnabled = !state.finished && state.lastCorrect != null && deed?.canPlay != false,
                onNext = { onAction(BargainAction.Next(state.current)) },
            )
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(emoji = "⚖️", title = stringResource(R.string.deeds_bargain_complete),
            reward = state.correctAnswers * 2, onAgain = { onAction(BargainAction.Restart) }, onHub = onBack)
    }
}

@Composable
private fun OfferCard(offer: BargainOffer, showAsAnswer: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DeedColors.CreamCard)
            .then(if (showAsAnswer) Modifier.border(3.dp, DeedColors.Lime, shape)
                else Modifier.border(1.dp, DeedColors.Border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { selected = showAsAnswer }
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.deeds_bargain_pieces, offer.count),
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text)
        Spacer(Modifier.height(10.dp))
        CoinChip(coinAmount(offer.totalPrice.toLong()))
    }
}
