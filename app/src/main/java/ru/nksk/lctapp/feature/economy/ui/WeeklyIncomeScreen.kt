package ru.nksk.lctapp.feature.economy.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** Replaceable presentation copy. Receiving this state never credits the player's balance. */
internal data class WeeklyIncomeUiState(
    val amount: Long = 100,
    val header: String = "Новая неделя",
    val amountCaption: String = "монет на 7 дней",
    val title: String = "Новый запас на неделю",
    val description: String = "Монеты уже в общей копилке. Теперь распредели их по статьям.",
    val nextIncome: String = "Следующее пополнение — через 7 дней",
    val hint: String = "План поможет позаботиться о нужном и приблизиться к цели.",
    val action: String = "Распределить монеты",
)

@Composable
internal fun WeeklyIncomeScreen(
    state: WeeklyIncomeUiState,
    onPlan: () -> Unit,
    onBack: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(GameInk)) {
        val landscape = maxWidth >= 600.dp && maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                IncomeHero(state, onBack, Modifier.weight(1f).fillMaxHeight())
                IncomeInvitation(state, onPlan, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                IncomeHero(state, onBack, Modifier.fillMaxWidth().weight(0.46f))
                IncomeInvitation(state, onPlan, Modifier.fillMaxWidth().weight(0.54f))
            }
        }
    }
}

@Composable
private fun IncomeHero(state: WeeklyIncomeUiState, onBack: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        Image(painterResource(R.drawable.event_weekly_income_background), null, Modifier.matchParentSize(),
            contentScale = ContentScale.Crop, alignment = Alignment.BottomCenter)
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = GameInk.copy(alpha = 0.9f)) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.menu_chevron), "В главное меню",
                            Modifier.size(20.dp).rotate(180f), tint = Color.White)
                    }
                }
                Surface(shape = RoundedCornerShape(28.dp), color = GameInk.copy(alpha = 0.9f)) {
                    Text(state.header, Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color.White, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                Surface(shape = RoundedCornerShape(28.dp), color = GameInk.copy(alpha = 0.94f)) {
                    Column(Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+${state.amount}", color = AdventureLime, fontFamily = Rubik,
                            fontWeight = FontWeight.ExtraBold, fontSize = 60.sp)
                        Text(state.amountCaption, color = Color.White, fontFamily = Nunito,
                            fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, textAlign = TextAlign.Center)
                    }
                }
                // Reserve the foreground for the coin pile painted into the event background.
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun IncomeInvitation(state: WeeklyIncomeUiState, onPlan: () -> Unit, modifier: Modifier) {
    Surface(modifier, color = GamePaper, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.title, color = GameInk, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp, textAlign = TextAlign.Center)
                Text(state.description, color = GameInk, fontFamily = Nunito, fontSize = 17.sp,
                    textAlign = TextAlign.Center)
                Surface(color = Color(0xFFECE5FF), shape = RoundedCornerShape(18.dp)) {
                    Text(state.nextIncome, Modifier.fillMaxWidth().padding(16.dp),
                        color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, textAlign = TextAlign.Center)
                }
                Text(state.hint, color = Color(0xFF6B6394), fontFamily = Nunito,
                    fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Button(onClick = onPlan, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 10.dp)
                .heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AdventureLime, contentColor = GameInk)) {
                Text(state.action, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    textAlign = TextAlign.Center)
            }
        }
    }
}
