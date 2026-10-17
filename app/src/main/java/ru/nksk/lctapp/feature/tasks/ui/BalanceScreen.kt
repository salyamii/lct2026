package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.minigame.BalanceState
import ru.nksk.lctapp.domain.minigame.DeedRewardPreview

/** Дело «Монетный баланс»: сними необязательные траты, чтобы расходы не перевешивали доходы. */
@Composable
fun BalanceGameScreen(
    uiState: BalanceUiState,
    onAction: (BalanceAction) -> Unit,
    onBack: () -> Unit,
    deed: DeedGamePresentation? = null,
) {
    val state = uiState.game
    FinGameBoard(
        deed = deed,
        title = stringResource(R.string.deeds_balance_title),
        sceneRes = R.drawable.location_butcher_shop,
        preview = DeedRewardPreview.fromBalance(state),
        onBack = onBack,
    ) {
        Text(
            deed?.instructions ?: stringResource(R.string.deeds_balance_hint),
            fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold,
            fontFamily = Rubik, color = DeedColors.Text,
        )
        Spacer(Modifier.height(8.dp))
        DeedChip(stringResource(R.string.deeds_question,
            minOf(state.current + 1, BalanceState.ROUNDS), BalanceState.ROUNDS))
        Spacer(Modifier.height(14.dp))
        if (!state.finished || deed != null) {
            val locked = state.lastCorrect != null || deed?.canPlay == false
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.deeds_balance_income),
                    fontSize = 15.sp, fontFamily = Nunito, color = DeedColors.TextSoft)
                CoinChip(coinAmount(state.weekIncome.toLong()), lime = true)
            }
            Spacer(Modifier.height(10.dp))
            state.weekExpenses.forEachIndexed { index, expense ->
                val removed = index in state.removed
                ExpenseRow(expense.name, expense.amount, expense.essential, removed, locked) {
                    onAction(BalanceAction.Toggle(index))
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.deeds_balance_expenses),
                    fontSize = 15.sp, fontFamily = Nunito, color = DeedColors.TextSoft)
                CoinChip(coinAmount(state.remainingExpenses.toLong()),
                    lime = state.balanced)
            }
            Spacer(Modifier.height(14.dp))
            if (state.lastCorrect == null) {
                DeedButton(stringResource(R.string.deeds_balance_confirm),
                    onClick = { onAction(BalanceAction.Confirm) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.balanced && deed?.canPlay != false)
            } else {
                FinFeedback(
                    text = stringResource(when {
                        state.lastCorrect == true -> R.string.deeds_balance_correct
                        state.removed.any { state.weekExpenses[it].essential } -> R.string.deeds_balance_wrong_dropped
                        else -> R.string.deeds_balance_wrong_over
                    }),
                    buttonText = stringResource(if (minOf(state.current, state.income.lastIndex) == state.income.lastIndex)
                        R.string.deeds_price_finish else R.string.deeds_price_next),
                    buttonEnabled = deed?.canPlay != false,
                    onNext = { onAction(BalanceAction.Next) },
                )
            }
        }
    }

    if (state.finished && deed == null) {
        DeedResultSheet(emoji = "⚖️", title = stringResource(R.string.deeds_balance_complete),
            reward = state.correctRounds * 2, onAgain = { onAction(BalanceAction.Restart) }, onHub = onBack)
    }
}

@Composable
private fun ExpenseRow(name: String, amount: Int, essential: Boolean, removed: Boolean,
    locked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (removed) DeedColors.CreamSoft else DeedColors.CreamCard)
            .clickable(enabled = !locked, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontFamily = Nunito, color = DeedColors.Text,
                textDecoration = if (removed) TextDecoration.LineThrough else null)
            if (essential) {
                Text(stringResource(R.string.deeds_balance_expense_essential),
                    fontSize = 12.sp, fontFamily = Nunito, color = DeedColors.TextSoft)
            }
        }
        Text(coinAmount(amount.toLong()), fontSize = 14.sp, fontFamily = Nunito,
            color = if (removed) DeedColors.TextSoft else DeedColors.Text,
            textDecoration = if (removed) TextDecoration.LineThrough else null)
    }
}
