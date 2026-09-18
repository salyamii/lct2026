package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.feature.tasks.logic.PriceQuizState

private val GOODS_EMOJI = listOf("🗺️", "📜", "🕯️", "🧭", "⚗️", "🏮")

/** Дело «Сверка счетов»: пять вопросов, найди самую дорогую покупку. */
@Composable
fun PriceQuizScreen(onBack: () -> Unit, onFinish: (Int) -> Unit) {
    var state by remember { mutableStateOf(PriceQuizState.create()) }

    LaunchedEffect(state.current, state.lastCorrect) {
        if (state.lastCorrect != null) {
            delay(750)
            state = state.next()
        }
    }
    LaunchedEffect(state.finished) {
        if (state.finished) onFinish(state.reward)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            Image(
                painterResource(R.drawable.location_workshop),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop,
            )
            DeedHeader("Сверка счетов", onBack = onBack)
        }
        DeedSheet(modifier = Modifier.weight(1f)) {
            Text(
                "Какая покупка дороже?",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeedChip("Счёт ${minOf(state.current + 1, PriceQuizState.QUESTION_COUNT)} из ${PriceQuizState.QUESTION_COUNT}")
                CoinChip("Награда · +${state.reward}")
            }
            Spacer(Modifier.height(14.dp))
            if (!state.finished) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InvoiceCard(
                        emoji = GOODS_EMOJI[state.current % GOODS_EMOJI.size],
                        amount = state.question.leftAmount,
                        showAsAnswer = state.lastCorrect != null && !state.question.leftIsBigger,
                        onClick = { state = state.answer(pickedLeft = true) },
                        modifier = Modifier.weight(1f),
                    )
                    InvoiceCard(
                        emoji = GOODS_EMOJI[(state.current + 3) % GOODS_EMOJI.size],
                        amount = state.question.rightAmount,
                        showAsAnswer = state.lastCorrect != null && state.question.leftIsBigger,
                        onClick = { state = state.answer(pickedLeft = false) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when (state.lastCorrect) {
                        true -> "Верно! Счета сходятся 🎉"
                        false -> "Не сходится — гляди внимательнее"
                        null -> "Тапни по товару с большей ценой"
                    },
                    fontSize = 14.sp,
                    fontFamily = Nunito,
                    color = if (state.lastCorrect == true) DeedColors.Text else DeedColors.TextSoft,
                )
            }
        }
    }

    if (state.finished) {
        DeedResultSheet(
            emoji = "🪙",
            title = "Счета сверены!",
            reward = state.reward,
            onAgain = { state = PriceQuizState.create() },
            onHub = onBack,
        )
    }
}

@Composable
private fun InvoiceCard(
    emoji: String,
    amount: Int,
    showAsAnswer: Boolean,
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
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        CoinChip("$amount монет")
    }
}
