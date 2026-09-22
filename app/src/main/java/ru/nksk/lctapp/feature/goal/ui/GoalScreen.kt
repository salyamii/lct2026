package ru.nksk.lctapp.feature.goal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.core.ui.components.GameBody
import ru.nksk.lctapp.core.ui.components.GameButton
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

private val GoalBackground = Color(0xFFF8FEEE)
private val GoalAccent = Color(0xFF3E31B8)

/** Native scroll/insets instead of the mockup's fixed viewport and simulated system bar. */
@Composable
internal fun GoalScreen(state: GoalUiState, onBack: () -> Unit, onAction: (GoalAction) -> Unit) {
    var missingCoins by remember { mutableStateOf<Long?>(null) }
    missingCoins?.let { missing ->
        AlertDialog(onDismissRequest = { missingCoins = null },
            title = { Text("Не хватает монет", fontFamily = Rubik) },
            text = { GameBody("В «Коплю» не хватает $missing монет для покупки.") },
            confirmButton = { TextButton(onClick = { missingCoins = null }) { Text("Понятно") } })
    }
    Column(Modifier.fillMaxSize().background(GoalBackground)) {
        Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(GameInk, GoalAccent)))
            .statusBarsPadding().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (state.showList) onBack() else onAction(GoalAction.ShowList) }) {
                Text("Назад", color = Color.White, fontFamily = Nunito)
            }
            Spacer(Modifier.weight(1f))
            if (!state.loading && !state.failed) Text("Коплю: ${state.balance}", color = Color.White,
                fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 620.dp).fillMaxSize()
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    state.loading -> CircularProgressIndicator(color = GameInk)
                    state.failed -> {
                        GameBody("Не удалось загрузить цель. Сохранение осталось на месте.")
                        GameButton("Повторить") { onAction(GoalAction.Retry) }
                    }
                    state.showList -> {
                        Text(if (state.campaignComplete) "Все цели выполнены" else "Большие цели",
                            color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
                            fontSize = 27.sp, modifier = Modifier.semantics { heading() })
                        GameBody("${state.petName} может выбрать личный проект. Общая история идёт своим чередом, а финал каждой главы ждёт собранный комплект.")
                        GameBody("Завершено проектов: ${state.completedProjectCount} из ${state.projects.size}")
                        state.projects.forEach { project ->
                            Surface(color = GamePaper, shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, if (project.status == GoalProjectStatus.ACTIVE) AdventureLime else Color(0xFFD9DCF5))) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(project.title, color = GameInk, fontFamily = Nunito,
                                        fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                                    GameBody(project.description)
                                    GameBody("Частей: ${project.parts} · всего ${project.price} монет")
                                    Text(project.hint, color = GoalAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                                    GameButton(if (project.status == GoalProjectStatus.ACTIVE) "Открыть текущую цель" else "Посмотреть состав", !state.busy) {
                                        onAction(GoalAction.View(project.id))
                                    }
                                }
                            }
                        }
                        state.message?.let { GameBody(it) }
                    }
                    else -> {
                        TextButton(onClick = { onAction(GoalAction.ShowList) }, enabled = !state.busy) {
                            Text("Все большие цели", color = GoalAccent, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                        }
                        Text(if (state.completedProject) "Завершённый проект" else if (state.selected) "Твоя большая цель" else "Большая цель",
                            color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
                            fontSize = 27.sp, modifier = Modifier.semantics { heading() })
                        Surface(color = GamePaper, shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, AdventureLime), shadowElevation = 4.dp) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(state.title, color = GameInk, fontFamily = Nunito,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                                Text("Частей: ${state.parts.size} · всего ${state.totalPrice} монет", color = GameInk,
                                    fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                                GameBody(state.description)
                                if (state.selected || state.completedProject) {
                                    Text("Куплено ${state.collected} из ${state.parts.size} · осталось ${state.remainingPrice} монет",
                                        color = GoalAccent, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                                    LinearProgressIndicator(progress = { state.collected.toFloat() / state.parts.size.coerceAtLeast(1) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)),
                                        color = GoalAccent, trackColor = Color(0xFFE1DFF4))
                                }
                                Text("Что входит в цель", color = GoalAccent, fontFamily = Nunito,
                                    fontWeight = FontWeight.ExtraBold)
                                state.parts.forEach { part ->
                                    GoalPart(part, state.selected, state.busy) {
                                        if (part.missingCoins != null) missingCoins = part.missingCoins
                                        else onAction(GoalAction.Buy(checkNotNull(state.goalId), part.id))
                                    }
                                }
                                GameBody(state.storyHint)
                                if (state.selected) GameBody("Покупка занимает шаг дня. Не забудь оставить монеты на еду.")
                            }
                        }
                        state.message?.let { GameBody(it) }
                        if (state.canSelect) GameButton("Выбрать эту цель", !state.busy) {
                            onAction(GoalAction.Select(checkNotNull(state.goalId)))
                        }
                        GameButton("На главный экран", !state.busy, onBack)
                    }
                }
            }
            state.celebration?.let {
                Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
                    shape = RoundedCornerShape(20.dp), color = GameInk, shadowElevation = 6.dp) {
                    Text(it, Modifier.padding(16.dp), color = Color.White, fontFamily = Nunito, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    state.confirmation?.let { confirmation ->
        AlertDialog(onDismissRequest = { if (!state.busy) onAction(GoalAction.CancelPurchase) },
            containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk,
            title = { Text("На еду может не хватить", fontFamily = Rubik, fontWeight = FontWeight.Bold) },
            text = { GameBody("${confirmation.itemTitle} стоит ${confirmation.price} монет. После покупки останется ${confirmation.remainingBalance}, а на обычную еду до конца недели нужно ${confirmation.foodNeeded}. Купить сейчас?") },
            confirmButton = { TextButton(onClick = { onAction(GoalAction.ConfirmPurchase) }, enabled = !state.busy) {
                Text("Всё равно купить", color = GameInk, fontWeight = FontWeight.Bold)
            } },
            dismissButton = { TextButton(onClick = { onAction(GoalAction.CancelPurchase) }, enabled = !state.busy) {
                Text("Отложить покупку", color = GameInk)
            } })
    }
}

@Composable
private fun GoalPart(part: GoalPartUiState, selected: Boolean, busy: Boolean, onBuy: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (part.owned) AdventureLime else Color(0xFFD9DCF5))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(part.title, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (part.owned) "Куплено" else "${part.price} монет", color = GoalAccent,
                    fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
            }
            GameBody(part.description)
            if (selected && !part.owned) {
                Button(onClick = onBuy, enabled = (part.canBuy || part.missingCoins != null) && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (part.canBuy) AdventureLime else Color(0xFFE7E5F0),
                        contentColor = if (part.canBuy) GameInk else Color(0xFF55506B),
                        disabledContainerColor = Color(0xFFE7E5F0), disabledContentColor = Color(0xFF55506B))) {
                    Text("Купить за ${part.price} монет", fontFamily = Rubik, fontWeight = FontWeight.SemiBold)
                }
                part.blockedMessage?.let { GameBody(it) }
            }
        }
    }
}
