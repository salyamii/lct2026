package ru.nksk.lctapp.feature.day.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigInteger
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.AdventurePrimaryButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameCardLayout
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GameTitle
import ru.nksk.lctapp.core.ui.components.gameScene
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

private val DiaryRule = Color(0xFFE5D9BD)
private val DiarySecondary = Color(0xFF685A48)

/** Keep the approved night scene and bed above the day's real, ordered journal entries. */
@Composable
internal fun DaySummaryScreen(state: DayUiState, onAction: (DayAction) -> Unit, onBack: () -> Unit) {
    val summary = state.summary ?: return
    GameCardLayout(
        category = "Итоги дня",
        scene = gameScene(state.scene),
        character = state.restingPetRes,
        onBack = onBack,
        sceneDim = .78f,
        characterDescription = "${state.petName} спит",
    ) {
        GameTitle("День ${summary.day} завершён")
        if (state.reflectionAvailable) {
            Surface(Modifier.fillMaxWidth(), color = Color(0xFFEDE9F7), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiarySectionTitle("А что, если…")
                    AdventureBody("Посмотрим, что изменилось бы, если поступить иначе?")
                    OutlinedButton(onClick = { onAction(DayAction.OpenReflection) }, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFC9C0DF)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk)) {
                        Text("Посмотреть другой путь", fontFamily = Nunito, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (summary.activities.isNotEmpty()) {
            DiarySectionTitle("Что сделали за день")
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                summary.activities.forEach { DiaryEntry(it) }
            }
        }
        HorizontalDivider(color = DiaryRule)
        DiaryMoney(summary)
        summary.detailsNote?.let { DiaryNote(it) }
        summary.adjustmentNote?.let { DiaryNote(it) }
        if (summary.availableBalance != null || summary.savingsBalance != null) {
            HorizontalDivider(color = DiaryRule)
            DiarySectionTitle("Монеты сейчас")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                summary.availableBalance?.let {
                    DiaryBalance("Доступно", it, R.drawable.budget_reserve)
                }
                summary.savingsBalance?.let {
                    DiaryBalance("В копилке", it, R.drawable.budget_savings)
                }
            }
        }
        state.message?.let { AdventureBody(it) }
        state.actionNotice?.let { AdventureBody(it) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.primary?.let { label ->
            AdventurePrimaryButton(label, { onAction(DayAction.Primary) }, enabled = !state.busy)
        }
    }
}

@Composable
private fun DiarySectionTitle(text: String) {
    Text(text, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp, lineHeight = 24.sp)
}

@Composable
private fun DiaryEntry(entry: DaySummaryRow) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GameArtwork(entry.kind.diaryIcon(), null, Modifier.size(38.dp))
        Column(Modifier.weight(1f).drawBehind {
            val margin = -6.dp.toPx()
            drawLine(DiaryRule, Offset(margin, 0f), Offset(margin, size.height), 1.dp.toPx())
        }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(entry.label, color = GameInk, fontFamily = Nunito, fontSize = 16.sp, lineHeight = 23.sp)
            if (entry.value.isNotBlank()) Text(entry.value, color = DiarySecondary,
                fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@DrawableRes
private fun DaySummaryRowKind.diaryIcon(): Int = when (this) {
    DaySummaryRowKind.EVENT -> R.drawable.deed_goods_map
    DaySummaryRowKind.STORY -> R.drawable.deed_goods_scroll
    DaySummaryRowKind.WORK -> R.drawable.menu_tasks
    DaySummaryRowKind.MEAL -> R.drawable.budget_needs
    DaySummaryRowKind.PURCHASE -> R.drawable.budget_wants
    DaySummaryRowKind.FOUND -> R.drawable.deed_goods_compass
    DaySummaryRowKind.COINS -> R.drawable.menu_coin
    DaySummaryRowKind.DECISION -> R.drawable.deed_goods_map
}

@Composable
private fun DiaryMoney(summary: DaySummaryUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DiarySectionTitle("Монеты за день")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 260.dp || LocalDensity.current.fontScale >= 1.4f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryMoneyCard("Получили", summary.income, Modifier.fillMaxWidth())
                    DiaryMoneyCard("Потратили", summary.spending, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryMoneyCard("Получили", summary.income, Modifier.weight(1f).fillMaxHeight())
                    DiaryMoneyCard("Потратили", summary.spending, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        summary.netChange?.let { net ->
            val amount = if (net.signum() > 0) "+${diaryNumber(net)}" else diaryNumber(net)
            DiaryNote("Изменение за день: $amount монет")
        }
    }
}

@Composable
private fun DiaryMoneyCard(label: String, amount: BigInteger?, modifier: Modifier) {
    Surface(modifier, color = Color(0xFFF3E9D3), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = DiarySecondary, fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameArtwork(R.drawable.menu_coin, null, Modifier.size(22.dp))
                Text(amount?.let(::diaryNumber) ?: "—", Modifier.weight(1f), color = GameInk,
                    fontFamily = Rubik, fontSize = 22.sp, lineHeight = 28.sp)
            }
        }
    }
}

@Composable
private fun DiaryBalance(label: String, amount: Long, @DrawableRes illustration: Int) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GameArtwork(illustration, null, Modifier.size(32.dp))
        Text(label, Modifier.weight(1f), color = GameInk, fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp)
        Text(diaryNumber(BigInteger.valueOf(amount)), Modifier.widthIn(max = 160.dp), color = GameInk,
            fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 24.sp)
    }
}

@Composable
private fun DiaryNote(text: String) {
    Text(text, color = DiarySecondary, fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp)
}

private fun diaryNumber(value: BigInteger): String {
    val digits = value.abs().toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (value.signum() < 0) "−$digits" else digits
}
