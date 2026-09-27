package ru.nksk.lctapp.core.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** Shows reconciled movements from the latest confirmed plan, without changing the saved world. */
@Composable
internal fun BudgetHistoryDetails(history: BudgetHistoryUi, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HistoryHeading("Монеты под рукой")
        HistoryAmount("Было", history.startingAvailable)
        if (history.income > 0) HistoryAmount("Получили", history.income, "+")
        if (history.spentAvailable > 0) HistoryAmount("Потратили", history.spentAvailable, "−")
        if (history.deposited > 0) HistoryAmount("Положили в копилку", history.deposited, "−")
        if (history.withdrawn > 0) HistoryAmount("Взяли из копилки", history.withdrawn, "+")
        HorizontalDivider(color = GameInk.copy(alpha = .15f))
        HistoryAmount("Сейчас доступно", history.resultingAvailable, strong = true)
        HorizontalDivider(color = GameInk.copy(alpha = .15f))
        HistoryHeading("В копилке")
        HistoryAmount("Было", history.startingSavings)
        if (history.deposited > 0) HistoryAmount("Положили", history.deposited, "+")
        if (history.withdrawn > 0) HistoryAmount("Взяли обратно", history.withdrawn, "−")
        if (history.spentSavings > 0) HistoryAmount("Купили для цели", history.spentSavings, "−")
        HorizontalDivider(color = GameInk.copy(alpha = .15f))
        HistoryAmount("Сейчас в копилке", history.resultingSavings, strong = true)
    }
}

@Composable
private fun HistoryHeading(text: String) {
    Text(text, color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

@Composable
private fun HistoryAmount(label: String, amount: Long, sign: String = "", strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
        Text("$sign$amount", color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp, lineHeight = 22.sp)
    }
}
