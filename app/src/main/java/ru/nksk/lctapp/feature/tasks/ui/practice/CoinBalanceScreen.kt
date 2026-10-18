package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview
import ru.nksk.lctapp.feature.tasks.ui.DeedGamePresentation
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.domain.minigame.BalanceVerdict
import ru.nksk.lctapp.domain.minigame.CoinBalanceAction
import ru.nksk.lctapp.domain.minigame.CoinBalanceState
import ru.nksk.lctapp.domain.minigame.PracticeExpense
import ru.nksk.lctapp.feature.tasks.ui.DeedButton
import ru.nksk.lctapp.feature.tasks.ui.DeedChip
import ru.nksk.lctapp.feature.tasks.ui.DeedColors

@Composable
fun CoinBalanceScreen(
    state: CoinBalanceState,
    onAction: (CoinBalanceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val feedback = remember { BringIntoViewRequester() }
    LaunchedEffect(state.current, state.verdict) {
        if (state.verdict != null) feedback.bringIntoView()
    }
    PracticeScaffold(stringResource(R.string.practice_balance_title), R.drawable.location_city,
        R.drawable.npc_caretaker_explaining, stringResource(state.round.scenario.promptRes, state.round.income),
        state.current, state.rounds.size, state.correctAnswers, state.finished,
        { onAction(CoinBalanceAction.Restart) }, onBack, deed, DeedRewardPreview.fromCoinBalance(state)) {
        PracticeBoard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PracticeText(stringResource(R.string.practice_received), bold = true)
                PracticeText(stringResource(R.string.practice_spent), bold = true)
            }
            GameArtwork(R.drawable.prop_coin_balance_scales, null, Modifier.fillMaxWidth().height(132.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                DeedChip(stringResource(R.string.practice_price, state.round.income), lime = true)
                DeedChip(stringResource(R.string.practice_price, state.spent), lime = state.remainder >= 0)
            }
            state.round.expenses.chunked(2).forEach { expenses ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    expenses.forEach { expense ->
                        ExpenseCard(expense, expense.item in state.removed,
                            enabled = deed?.canPlay != false && state.started && state.verdict == null, modifier = Modifier.weight(1f),
                            onToggle = { onAction(CoinBalanceAction.Toggle(expense.item, state.current)) })
                    }
                    if (expenses.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            PracticeText(stringResource(if (state.remainder >= 0) R.string.practice_budget_left
                else R.string.practice_budget_short, kotlin.math.abs(state.remainder)), bold = true)
        }
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(feedback),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !state.started -> {
                    PracticeText(stringResource(R.string.practice_balance_intro))
                    DeedButton(stringResource(R.string.practice_start), { onAction(CoinBalanceAction.Start) }, Modifier.fillMaxWidth())
                }
                state.verdict == null -> {
                    PracticeText(stringResource(R.string.practice_balance_hint))
                    DeedButton(stringResource(R.string.practice_check_balance),
                        { onAction(CoinBalanceAction.Check(state.current)) }, Modifier.fillMaxWidth())
                }
                else -> {
                    val correct = state.verdict == BalanceVerdict.BALANCED
                    val explanation = when (requireNotNull(state.verdict)) {
                        BalanceVerdict.BALANCED -> stringResource(R.string.practice_budget_ok,
                            state.spent, state.round.income, state.remainder)
                        BalanceVerdict.OVERSPENT -> stringResource(R.string.practice_budget_over, -state.remainder)
                        BalanceVerdict.INCOMPLETE_PLAN ->
                            state.unmetNeeds.map { stringResource(it.explanationRes) }.joinToString(" ")
                    }
                    FeedbackNote(correct, explanation)
                    DeedButton(stringResource(if (!correct) R.string.practice_edit_balance
                        else if (state.current == state.rounds.lastIndex) R.string.practice_finish else R.string.practice_next),
                        { onAction(if (correct) CoinBalanceAction.Next(state.current) else CoinBalanceAction.Edit(state.current)) },
                        Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ExpenseCard(expense: PracticeExpense, removed: Boolean, enabled: Boolean,
                        modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val status = stringResource(if (removed) R.string.practice_removed else R.string.practice_kept)
    Column(modifier.clip(shape).background(if (removed) ColorRemoved else DeedColors.CreamCard)
        .border(1.dp, DeedColors.Border, shape)
        .toggleable(value = !removed, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
        .semantics(mergeDescendants = true) { stateDescription = status }.padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameArtwork(expense.item.artwork, null, Modifier.size(36.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.practice_price, expense.cost), color = PracticeInk,
                fontFamily = Nunito, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(stringResource(expense.item.titleRes), color = PracticeInk,
            fontFamily = Nunito, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            textDecoration = if (removed) TextDecoration.LineThrough else TextDecoration.None)
        if (removed) Text(status, color = PracticeInk,
            fontFamily = Nunito, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private val ColorRemoved = androidx.compose.ui.graphics.Color(0xFFE9DFCB)
