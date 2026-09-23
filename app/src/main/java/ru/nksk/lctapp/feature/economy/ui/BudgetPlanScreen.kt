package ru.nksk.lctapp.feature.economy.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

private val PlanMuted = Color(0xFF6B6394)
private val PlanPlum = Color(0xFF302451)

/** Native budget editor; state and durable operations are owned by the entry ViewModel. */
@Composable
internal fun BudgetPlanScreen(state: BudgetUiState, onAmountChange: (BudgetArticle, Long) -> Unit,
    onConfirm: () -> Unit, onBack: () -> Unit,
    onAdjust: ((BudgetArticle, Boolean) -> Unit)? = null) {
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().background(GameInk)) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        Column(Modifier.fillMaxSize()) {
            PlanHeader(onBack)
            Surface(Modifier.weight(1f).fillMaxWidth(), color = GamePaper,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                if (wide) Row(Modifier.fillMaxSize().windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                    Column(Modifier.weight(0.42f).fillMaxHeight().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PlanSummary(state)
                            PlanDistribution(state)
                        }
                        PlanConfirm(state, onConfirm)
                    }
                    PlanArticles(state, Modifier.weight(0.58f).fillMaxHeight(),
                        { info = it.name }, { if (state.actionsEnabled) editing = it.name }, onAmountChange, onAdjust)
                } else Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                    Column(Modifier.weight(1f).align(Alignment.CenterHorizontally).widthIn(max = 600.dp)
                        .fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlanSummary(state)
                        BudgetArticle.entries.forEach { article ->
                            PlanArticle(article, state, { info = article.name }, { if (state.actionsEnabled) editing = article.name }, onAmountChange, onAdjust)
                        }
                        PlanDistribution(state)
                    }
                    Box(Modifier.fillMaxWidth().background(GamePaper)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            PlanConfirm(state, onConfirm)
                        }
                    }
                }
            }
        }
    }
    info?.let { name ->
        val article = BudgetArticle.valueOf(name)
        AlertDialog(onDismissRequest = { info = null }, containerColor = GamePaper,
            title = { Text(article.title, color = GameInk, fontFamily = Rubik) },
            text = { Text(article.explanation, color = GameInk, fontFamily = Nunito) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Понятно", color = PlanPlum) } })
    }
    editing?.let { name ->
        val article = BudgetArticle.valueOf(name)
        BudgetAmountDialog(article, state.amount(article), state.amount(article) + state.unallocated,
            onDismiss = { editing = null }, onSave = { onAmountChange(article, it); editing = null },
            minimum = if (article == BudgetArticle.NEEDS) state.minimumNeeds else 0)
    }
}

@Composable
private fun PlanHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.location_city_evening), null,
            Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(GameInk.copy(alpha = 0.35f)))
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).clip(CircleShape).background(PlanPlum.copy(alpha = 0.9f))) {
                Icon(painterResource(R.drawable.menu_chevron), "Назад", Modifier.size(20.dp).rotate(180f), tint = Color.White)
            }
            Surface(Modifier.weight(1f), color = PlanPlum.copy(alpha = 0.9f), shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))) {
                Text("План на 7 дней", Modifier.padding(horizontal = 12.dp, vertical = 12.dp).semantics { heading() },
                    color = Color.White, fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun PlanSummary(state: BudgetUiState) {
    Surface(color = PlanPlum, shape = RoundedCornerShape(22.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(10.dp)) {
            if (state.weeklyIncome == 0L) {
                PlanTotal(state, Modifier.fillMaxWidth())
            } else if (maxWidth < 300.dp * LocalDensity.current.fontScale) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanTotal(state)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
                    PlanIncome(state)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PlanTotal(state, Modifier.weight(1f))
                    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(54.dp).background(Color.White.copy(alpha = 0.2f)))
                    PlanIncome(state, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlanTotal(state: BudgetUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Всего монет", color = Color(0xFFE1D8F1), fontFamily = Nunito, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.menu_coin), null, Modifier.size(32.dp))
            Text(state.total.toString(), color = Color.White, fontFamily = Rubik, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun PlanIncome(state: BudgetUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Пришло", color = Color(0xFFE1D8F1), fontFamily = Nunito, fontSize = 15.sp)
        Text("+${state.weeklyIncome}", color = AdventureLime, fontFamily = Rubik, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("уже в общей сумме", color = Color(0xFFE1D8F1), fontFamily = Nunito, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlanHeading() = Text("Распредели монеты", color = GameInk, fontFamily = Rubik,
    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })

@Composable
private fun PlanArticles(state: BudgetUiState, modifier: Modifier, info: (BudgetArticle) -> Unit,
    edit: (BudgetArticle) -> Unit, change: (BudgetArticle, Long) -> Unit, adjust: ((BudgetArticle, Boolean) -> Unit)?) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PlanHeading()
        BudgetArticle.entries.forEach { PlanArticle(it, state, { info(it) }, { edit(it) }, change, adjust) }
        Text("Перераспределить монетки можно в любой момент.", color = PlanMuted, fontFamily = Nunito, fontSize = 13.sp)
    }
}

@Composable
private fun PlanArticle(article: BudgetArticle, state: BudgetUiState, info: () -> Unit, edit: () -> Unit,
    change: (BudgetArticle, Long) -> Unit, adjust: ((BudgetArticle, Boolean) -> Unit)?) {
    val amount = state.amount(article)
    val color = article.planColor
    Surface(color = Color.White.copy(alpha = 0.7f), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.5.dp, color)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val artSize = if (maxWidth < 310.dp) 64.dp else 80.dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(article.planArt), null, Modifier.size(artSize), contentScale = ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(end = 48.dp)) {
                            Text(article.title, color = GameInk, fontFamily = Rubik,
                                fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, lineHeight = 22.sp)
                            Text(if (article == BudgetArticle.NEEDS && state.minimumNeeds == 0L) "Еда и нужные вещи" else article.planSubtitle, color = PlanMuted, fontFamily = Nunito,
                                fontSize = 11.sp, lineHeight = 13.sp)
                        }
                        IconButton(onClick = info, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                            .semantics { contentDescription = "О статье ${article.title}" }) {
                            Text("i", color = PlanPlum, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlanStep("−", "Уменьшить ${article.title} на 5", state.actionsEnabled && amount - BUDGET_STEP >= (if (article == BudgetArticle.NEEDS) state.minimumNeeds else 0), false) {
                            if (adjust != null) adjust(article, false) else change(article, amount - BUDGET_STEP)
                        }
                        Surface(onClick = edit, enabled = state.actionsEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .semantics { contentDescription = "${article.title}: $amount монет. Ввести сумму" },
                            color = color.copy(alpha = 0.3f), shape = RoundedCornerShape(24.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$amount", color = GameInk, fontFamily = Nunito, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        val step = minOf(BUDGET_STEP, state.unallocated.coerceAtLeast(0))
                        PlanStep("+", "Увеличить ${article.title} на $step", state.actionsEnabled && step > 0, true) { if (adjust != null) adjust(article, true) else change(article, amount + step) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanStep(label: String, description: String, enabled: Boolean, dark: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick, Modifier.padding(2.dp).size(48.dp).semantics { contentDescription = description },
        enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (dark) PlanPlum else Color(0xFFECE5FF),
            contentColor = if (dark) Color.White else GameInk, disabledContainerColor = Color(0xFFEAE4EE),
            disabledContentColor = Color(0xFF9B91AF))) { Text(label, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PlanDistribution(state: BudgetUiState) {
    Surface(color = Color.White.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFD9CEF2))) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (state.canConfirm) "✓ " else "") + "Осталось распределить: ${state.unallocated}",
                color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
            )
            Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE3DDEE))
                .semantics {
                    contentDescription = BudgetArticle.entries.joinToString {
                        "${it.title}: ${state.amount(it)} из ${state.total}"
                    } + ". Не распределено: ${state.unallocated}"
                }) {
                BudgetArticle.entries.forEach { article ->
                    val amount = state.amount(article)
                    if (amount > 0) DistributionSegment(amount, state.total, article.planColor)
                }
                if (state.unallocated > 0) {
                    DistributionSegment(state.unallocated, state.total, Color(0xFFE3DDEE))
                }
            }
        }
    }
}

@Composable
private fun RowScope.DistributionSegment(amount: Long, total: Long, color: Color) {
    BoxWithConstraints(Modifier.weight(amount.toFloat()).fillMaxHeight().background(color),
        contentAlignment = Alignment.Center) {
        // Tiny shares retain their true width; their amounts remain available in the cards.
        val label = "$amount/$total"
        if (maxWidth >= (label.length * 6).dp * LocalDensity.current.fontScale) {
            Text(label, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun PlanConfirm(state: BudgetUiState, onConfirm: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.needs < state.minimumNeeds) Text("Добавь в Нужно ещё ${state.minimumNeeds - state.needs} монет", color = PlanMuted, fontFamily = Nunito)
        Button(onConfirm, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = state.canConfirm,
            shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = AdventureLime,
                contentColor = GameInk, disabledContainerColor = Color(0xFFE1D8ED), disabledContentColor = Color(0xFF9B8DB1))) {
            Text("Подтвердить бюджет", fontFamily = Rubik, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

private val BudgetArticle.planColor: Color get() = when (this) {
    BudgetArticle.NEEDS -> Color(0xFFFF7770)
    BudgetArticle.WANTS -> Color(0xFF4BA6F8)
    BudgetArticle.SAVINGS -> Color(0xFF79CD43)
    BudgetArticle.RESERVE -> Color(0xFFFFBD29)
}
private val BudgetArticle.planArt: Int @DrawableRes get() = when (this) {
    BudgetArticle.NEEDS -> R.drawable.budget_needs
    BudgetArticle.WANTS -> R.drawable.budget_wants
    BudgetArticle.SAVINGS -> R.drawable.budget_savings
    BudgetArticle.RESERVE -> R.drawable.budget_reserve
}
private val BudgetArticle.planSubtitle: String get() = when (this) {
    BudgetArticle.NEEDS -> "Минимум 35 · еда на неделю"
    BudgetArticle.WANTS -> "Игрушки и развлечения"
    BudgetArticle.SAVINGS -> "На большую покупку"
    BudgetArticle.RESERVE -> "На неожиданные траты"
}
