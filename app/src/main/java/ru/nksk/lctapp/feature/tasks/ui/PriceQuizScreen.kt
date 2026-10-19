package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview

// Порядок важен для пар вопросов: вопрос берёт товар [i] и [i+3].
private val GOODS_ART = listOf(
    R.drawable.deed_goods_map,
    R.drawable.deed_goods_scroll,
    R.drawable.deed_goods_lens,
    R.drawable.deed_goods_compass,
    R.drawable.deed_goods_star_plate,
    R.drawable.deed_goods_lantern,
)

/** Сравнение цен: каждый вопрос задаёт выбор более дорогой или дешёвой покупки. */
@Composable
fun PriceQuizScreen(
    uiState: PriceQuizUiState,
    onAction: (PriceQuizAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    val displayedIndex = minOf(state.current, state.questions.lastIndex)
    val displayedQuestion = state.questions[displayedIndex]
    val prompt = stringResource(if (displayedQuestion.pickLarger)
        R.string.deeds_price_prompt else R.string.deeds_price_cheaper_prompt)
    val feedback = remember { BringIntoViewRequester() }
    LaunchedEffect(state.current, state.lastCorrect) {
        if (state.lastCorrect != null) feedback.bringIntoView()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            DeedBackdrop(deed?.sceneRes ?: R.drawable.location_workshop,
                modifier = Modifier.fillMaxWidth().height(150.dp), blurred = deed?.storyAction != true)
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp)) }
            DeedHeader(deed?.title ?: stringResource(R.string.deeds_price_title), onBack = onBack)
        }
        DeedGameSheet(deed, DeedRewardPreview.fromComparison(state), modifier = Modifier.weight(1f)) {
            Text(
                listOfNotNull(deed?.instructions, prompt).joinToString("\n\n"),
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
                DeedChip(stringResource(R.string.deeds_question, displayedIndex + 1, state.questions.size))
                if (deed == null) CoinChip(stringResource(R.string.deeds_demo_reward, state.reward))
            }
            Spacer(Modifier.height(14.dp))
            // Connected work returns to the menu after saving. Keep the last pair
            // visible during that write instead of collapsing the whole game board.
            if (!state.finished || deed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InvoiceCard(
                        art = GOODS_ART[displayedIndex % GOODS_ART.size],
                        amount = displayedQuestion.leftAmount,
                        enabled = !state.finished && state.lastCorrect == null && deed?.canPlay != false,
                        showAsAnswer = if (state.finished) displayedQuestion.leftIsCorrect else uiState.leftIsAnswer,
                        onClick = { onAction(PriceQuizAction.Answer(pickedLeft = true, questionIndex = state.current)) },
                        modifier = Modifier.weight(1f),
                    )
                    InvoiceCard(
                        art = GOODS_ART[(displayedIndex + 3) % GOODS_ART.size],
                        amount = displayedQuestion.rightAmount,
                        enabled = !state.finished && state.lastCorrect == null && deed?.canPlay != false,
                        showAsAnswer = if (state.finished) !displayedQuestion.leftIsCorrect else uiState.rightIsAnswer,
                        onClick = { onAction(PriceQuizAction.Answer(pickedLeft = false, questionIndex = state.current)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().bringIntoViewRequester(feedback)) {
                    Text(
                        when (state.lastCorrect) {
                            true -> stringResource(R.string.deeds_correct)
                            false -> stringResource(R.string.deeds_incorrect)
                            null -> stringResource(when {
                                state.finished -> R.string.deeds_ready
                                displayedQuestion.pickLarger -> R.string.deeds_price_hint
                                else -> R.string.deeds_price_cheaper_hint
                            })
                        },
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        minLines = 2,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = Nunito,
                        color = DeedColors.Text,
                    )
                    Spacer(Modifier.height(8.dp))
                    DeedButton(
                        text = stringResource(if (displayedIndex == state.questions.lastIndex)
                            R.string.deeds_price_finish else R.string.deeds_price_next),
                        onClick = { onAction(PriceQuizAction.Next(state.current)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.finished && state.lastCorrect != null && deed?.canPlay != false,
                    )
                }
            }
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(
            emoji = "🪙",
            title = stringResource(R.string.deeds_price_complete),
            reward = state.reward,
            onAgain = { onAction(PriceQuizAction.Restart) },
            onHub = onBack,
        )
    }
}

@Composable
private fun InvoiceCard(
    art: Int,
    amount: Int,
    showAsAnswer: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DeedColors.CreamCard)
            .then(
                if (showAsAnswer) Modifier.border(3.dp, DeedColors.Lime, shape)
                else Modifier.border(1.dp, DeedColors.Border, shape)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { selected = showAsAnswer }
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GameArtwork(art,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(12.dp))
        CoinChip(coinAmount(amount.toLong()))
    }
}
