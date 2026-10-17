package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.domain.minigame.DiscountState

/** Дело «Настоящая скидка»: зачёркнутая цена против настоящей прежней. */
@Composable
fun DiscountGameScreen(
    uiState: DiscountUiState,
    onAction: (DiscountAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    FinGameBoard(
        deed = deed,
        title = stringResource(R.string.deeds_discount_title),
        sceneRes = R.drawable.location_butcher_shop,
        preview = DeedRewardPreview.fromDiscount(state),
        onBack = onBack,
    ) {
        Text(
            deed?.instructions ?: stringResource(R.string.deeds_discount_hint),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedChip(stringResource(R.string.deeds_question,
            minOf(state.current + 1, DiscountState.ROUNDS), DiscountState.ROUNDS))
        Spacer(Modifier.height(14.dp))
        if (!state.finished || deed != null) {
            val question = state.questions[minOf(state.current, state.questions.lastIndex)]
            val locked = state.lastCorrect != null || deed?.canPlay == false
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DeedColors.CreamCard)
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(question.item, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    fontFamily = Rubik, color = DeedColors.Text)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoinChip(coinAmount(question.stickerOld.toLong()), lime = false)
                    Text("→", fontSize = 18.sp, fontFamily = Nunito, color = DeedColors.TextSoft,
                        textDecoration = TextDecoration.LineThrough)
                    CoinChip(coinAmount(question.priceNow.toLong()), lime = true)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.deeds_discount_was, question.fairPrice),
                    fontSize = 14.sp, fontFamily = Nunito, color = DeedColors.TextSoft)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DeedButtonSoft(stringResource(R.string.deeds_discount_fake),
                    onClick = { onAction(DiscountAction.Answer(believedReal = false, questionIndex = state.current)) },
                    modifier = Modifier.weight(1f), enabled = !locked)
                DeedButton(stringResource(R.string.deeds_discount_real),
                    onClick = { onAction(DiscountAction.Answer(believedReal = true, questionIndex = state.current)) },
                    modifier = Modifier.weight(1f), enabled = !locked)
            }
            Spacer(Modifier.height(14.dp))
            FinFeedback(
                text = when (state.lastCorrect) {
                    true -> stringResource(R.string.deeds_correct)
                    false -> stringResource(R.string.deeds_incorrect)
                    null -> if (state.finished) stringResource(R.string.deeds_ready)
                        else stringResource(R.string.deeds_discount_hint)
                },
                buttonText = stringResource(if (minOf(state.current, state.questions.lastIndex) == state.questions.lastIndex)
                    R.string.deeds_price_finish else R.string.deeds_price_next),
                buttonEnabled = !state.finished && state.lastCorrect != null && deed?.canPlay != false,
                onNext = { onAction(DiscountAction.Next(state.current)) },
            )
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(emoji = "🏷️", title = stringResource(R.string.deeds_discount_complete),
            reward = state.correctAnswers * 2, onAgain = { onAction(DiscountAction.Restart) }, onHub = onBack)
    }
}
