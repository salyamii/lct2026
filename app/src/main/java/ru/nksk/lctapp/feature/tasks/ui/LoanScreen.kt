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
import ru.nksk.lctapp.domain.minigame.LoanOption
import ru.nksk.lctapp.domain.minigame.LoanState

/** Дело «Финансовая ловушка»: два пути с деньгами, выбери тот, что оставит больше. */
@Composable
fun LoanGameScreen(
    uiState: LoanUiState,
    onAction: (LoanAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    FinGameBoard(
        deed = deed,
        title = stringResource(R.string.deeds_loan_title),
        sceneRes = R.drawable.location_pier,
        preview = DeedRewardPreview.fromLoan(state),
        onBack = onBack,
    ) {
        Text(
            deed?.instructions ?: stringResource(R.string.deeds_loan_hint),
            fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Rubik, color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedChip(stringResource(R.string.deeds_question,
            minOf(state.current + 1, LoanState.SCENARIO_COUNT), LoanState.SCENARIO_COUNT))
        Spacer(Modifier.height(14.dp))
        if (!state.finished || deed != null) {
            val scenario = state.scenarios[minOf(state.current, state.scenarios.lastIndex)]
            val locked = state.lastCorrect != null || deed?.canPlay == false
            Text(scenario.prompt, fontSize = 16.sp, lineHeight = 22.sp,
                fontFamily = Nunito, color = DeedColors.Text)
            Spacer(Modifier.height(12.dp))
            OptionCard(scenario.first, enabled = !locked,
                showAsAnswer = state.lastCorrect != null && scenario.firstIsRight,
                onClick = { onAction(LoanAction.Choose(pickedFirst = true, scenarioIndex = state.current)) })
            Spacer(Modifier.height(8.dp))
            OptionCard(scenario.second, enabled = !locked,
                showAsAnswer = state.lastCorrect != null && !scenario.firstIsRight,
                onClick = { onAction(LoanAction.Choose(pickedFirst = false, scenarioIndex = state.current)) })
            Spacer(Modifier.height(14.dp))
            FinFeedback(
                text = when {
                    state.lastCorrect == null ->
                        if (state.finished) stringResource(R.string.deeds_ready)
                        else stringResource(R.string.deeds_loan_hint)
                    state.lastCorrect == true -> scenario.outcome
                    else -> stringResource(R.string.deeds_incorrect) + " " + scenario.outcome
                },
                buttonText = stringResource(if (minOf(state.current, state.scenarios.lastIndex) == state.scenarios.lastIndex)
                    R.string.deeds_price_finish else R.string.deeds_price_next),
                buttonEnabled = !state.finished && state.lastCorrect != null && deed?.canPlay != false,
                onNext = { onAction(LoanAction.Next(state.current)) },
            )
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(emoji = "📜", title = stringResource(R.string.deeds_loan_complete),
            reward = state.correctAnswers * 2, onAgain = { onAction(LoanAction.Restart) }, onHub = onBack)
    }
}

@Composable
private fun OptionCard(option: LoanOption, showAsAnswer: Boolean, enabled: Boolean,
    onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(DeedColors.CreamCard)
            .then(if (showAsAnswer) Modifier.border(3.dp, DeedColors.Lime, shape)
                else Modifier.border(1.dp, DeedColors.Border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { selected = showAsAnswer }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(option.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = Rubik, color = DeedColors.Text)
        Spacer(Modifier.height(4.dp))
        Text(option.detail, fontSize = 14.sp, lineHeight = 19.sp,
            fontFamily = Nunito, color = DeedColors.TextSoft)
    }
}
