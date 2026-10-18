package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.AdaptiveActionPanel
import ru.nksk.lctapp.core.ui.components.GameBackButton
import ru.nksk.lctapp.core.ui.components.GameSceneTitle
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** Replaceable presentation copy. Receiving this state never credits the player's balance. */
internal data class WeeklyIncomeUiState(
    val amount: Long = 100,
    val header: String = "Новая неделя",
    val amountCaption: String = "монет на эту неделю",
    val title: String = "Новый запас на неделю",
    val description: String = "Сначала позаботимся о еде. Остальное разделим между желаниями, большой целью и запасом на неожиданности.",
    val nextIncome: String = "Новые монеты получим через 7 игровых дней.",
    val hint: String = "Выделим часть на цель. Она попадёт в копилку при подтверждении бюджета.",
    val action: String = "Распределить монеты",
    val availableBalance: Long? = null,
    val savingsBalance: Long? = null,
    val reserveRemainder: Long = 0,
    val busy: Boolean = false,
    val reserveTransferred: Long? = null,
)

@Composable
internal fun WeeklyIncomeScreen(
    state: WeeklyIncomeUiState,
    onPlan: () -> Unit,
    onBack: () -> Unit,
    onDepositReserve: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(GameInk)) {
        val landscape = maxWidth >= 600.dp && maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                IncomeHero(state, onBack, Modifier.weight(1f).fillMaxHeight())
                IncomeInvitation(state, onPlan, onDepositReserve, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                IncomeHero(state, onBack, Modifier.fillMaxWidth().weight(0.46f))
                IncomeInvitation(state, onPlan, onDepositReserve, Modifier.fillMaxWidth().weight(0.54f))
            }
        }
    }
}

@Composable
private fun IncomeHero(state: WeeklyIncomeUiState, onBack: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        GameArtwork(R.drawable.event_weekly_income_background, null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameBackButton(onClick = onBack, enabled = !state.busy, contentDescription = "В главное меню")
                GameSceneTitle(state.header, Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun IncomeInvitation(state: WeeklyIncomeUiState, onPlan: () -> Unit,
    onDepositReserve: () -> Unit, modifier: Modifier) {
    Surface(modifier, color = GamePaper, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter) {
            AdaptiveActionPanel(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                sectionSpacing = 10.dp,
                fillBody = true,
                actions = {
                    state.reserveTransferred?.takeIf { it > 0 }?.let { amount ->
                        Text("В копилку +$amount", color = GameInk, fontFamily = Nunito,
                            fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp)
                    }
                    if (state.reserveRemainder > 0) {
                        Text("Остаток запаса: ${coinAmount(state.reserveRemainder)}", color = GameInk,
                            fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
                        GameActionButton("Отложить остаток", onDepositReserve, enabled = !state.busy,
                            style = GameActionStyle.SECONDARY, minHeight = 48.dp)
                    }
                    GameActionButton(state.action, onPlan, enabled = !state.busy)
                },
            ) {
                Text(state.title, color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp, lineHeight = 28.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameArtwork(R.drawable.menu_coin, null, Modifier.size(32.dp), contentScale = ContentScale.Fit)
                    Text("+${state.amount} ${state.amountCaption}", color = GameInk, fontFamily = Nunito,
                        fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 25.sp)
                }
                Text(state.description, color = GameInk, fontFamily = Nunito, fontSize = 16.sp, lineHeight = 23.sp)
                IncomeDestinations()
                state.availableBalance?.takeIf { it != state.amount }?.let { available ->
                    Text("Вместе с оставшимися монетами у нас теперь $available.", color = GameInk,
                        fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp)
                }
                Text(state.hint, color = Color(0xFF6B6394), fontFamily = Nunito,
                    fontSize = 14.sp, lineHeight = 20.sp)
                Text(state.nextIncome, color = Color(0xFF6B6394), fontFamily = Nunito,
                    fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun IncomeDestinations() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.drawable.budget_needs to "Нужно",
            R.drawable.budget_wants to "Хочу",
            R.drawable.budget_savings to "В копилку",
            R.drawable.budget_reserve to "Запас",
        ).forEach { (art, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GameArtwork(art, null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                Text(label, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
