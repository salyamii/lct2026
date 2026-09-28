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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.PriceQuizState

// Порядок важен для пар вопросов: вопрос берёт товар [i] и [i+3].
private val GOODS_ART = listOf(
    R.drawable.deed_goods_map,
    R.drawable.deed_goods_scroll,
    R.drawable.deed_goods_lens,
    R.drawable.deed_goods_compass,
    R.drawable.deed_goods_star_plate,
    R.drawable.deed_goods_lantern,
)

/** Дело «Сверка счетов»: пять вопросов, найди самую дорогую покупку. */
@Composable
fun PriceQuizScreen(
    uiState: PriceQuizUiState,
    onAction: (PriceQuizAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
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
            GameArtwork(deed?.sceneRes ?: R.drawable.location_workshop,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            deed?.activityArtworkRes?.let { art -> GameArtwork(art, null,
                Modifier.align(Alignment.BottomEnd).size(92.dp)) }
            DeedHeader(deed?.title ?: stringResource(R.string.deeds_price_title), onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                deed?.instructions ?: if (deed != null) "Сравни значения и выбери большее. Ошибки уменьшают награду." else stringResource(R.string.deeds_price_prompt),
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
                DeedChip(stringResource(R.string.deeds_question, minOf(state.current + 1, PriceQuizState.QUESTION_COUNT), PriceQuizState.QUESTION_COUNT))
                if (deed?.storyAction != true) CoinChip(deed?.let { "Награда до ${it.maximumReward} монет" }
                    ?: stringResource(R.string.deeds_demo_reward, state.reward))
            }
            Spacer(Modifier.height(14.dp))
            // Connected work returns to the menu after saving. Keep the last pair
            // visible during that write instead of collapsing the whole game board.
            if (!state.finished || deed != null) {
                val displayedIndex = minOf(state.current, state.questions.lastIndex)
                val displayedQuestion = state.questions[displayedIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InvoiceCard(
                        art = GOODS_ART[displayedIndex % GOODS_ART.size],
                        amount = displayedQuestion.leftAmount,
                        enabled = !state.finished && state.lastCorrect == null && deed?.canPlay != false,
                        showAsAnswer = if (state.finished) displayedQuestion.leftIsBigger else uiState.leftIsAnswer,
                        onClick = { onAction(PriceQuizAction.Answer(pickedLeft = true, questionIndex = state.current)) },
                        modifier = Modifier.weight(1f),
                    )
                    InvoiceCard(
                        art = GOODS_ART[(displayedIndex + 3) % GOODS_ART.size],
                        amount = displayedQuestion.rightAmount,
                        enabled = !state.finished && state.lastCorrect == null && deed?.canPlay != false,
                        showAsAnswer = if (state.finished) !displayedQuestion.leftIsBigger else uiState.rightIsAnswer,
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
                            null -> stringResource(if (state.finished) R.string.deeds_ready else R.string.deeds_price_hint)
                        },
                        fontSize = 14.sp,
                        fontFamily = Nunito,
                        color = if (state.lastCorrect == true) DeedColors.Text else DeedColors.TextSoft,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (!state.finished && state.lastCorrect != null) {
                        Spacer(Modifier.height(12.dp))
                        DeedButton(
                            text = stringResource(if (state.current == state.questions.lastIndex)
                                R.string.deeds_price_finish else R.string.deeds_price_next),
                            onClick = { onAction(PriceQuizAction.Next(state.current)) },
                            enabled = deed?.canPlay != false,
                        )
                    }
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
        CoinChip(stringResource(R.string.deeds_amount, amount))
    }
}
