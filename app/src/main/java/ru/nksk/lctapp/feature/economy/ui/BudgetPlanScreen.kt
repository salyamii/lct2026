package ru.nksk.lctapp.feature.economy.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.game.toLiveAdventurePetPresentation
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.pet.PetState

private val PlanMuted = Color(0xFF6B6394)

/** The cards divide currently available coins; actual savings remain separate in the HUD. */
@Composable
internal fun BudgetPlanScreen(state: BudgetUiState, onAmountChange: (BudgetArticle, Long) -> Unit,
    onConfirm: () -> Unit, onBack: () -> Unit,
    onAdjust: ((BudgetArticle, Boolean) -> Unit)? = null,
    onDeposit: (() -> Unit)? = null, onWithdraw: (() -> Unit)? = null,
    contextId: String? = null,
    onContextPresented: ((String) -> Unit)? = null,
    onOpenSavings: (() -> Unit)? = null,
    pet: PetState? = null,
    interactionsBlocked: Boolean = false,
) {
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var balancesVisible by remember { mutableStateOf(false) }
    var foodNeedVisible by remember { mutableStateOf(false) }
    val savingsAction = onOpenSavings ?: onDeposit ?: onWithdraw
    val character = pet?.toLiveAdventurePetPresentation()
    val characterArt = character?.artworkRes
    LaunchedEffect(contextId, balancesVisible, foodNeedVisible) {
        if (balancesVisible && foodNeedVisible) contextId?.let { onContextPresented?.invoke(it) }
    }
    Box(Modifier.fillMaxSize().background(GameInk)) {
        GameArtwork(R.drawable.location_city_evening, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(GameInk.copy(alpha = .42f), Color.Transparent, GameInk.copy(alpha = .12f)))))
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val compact = maxHeight < 640.dp || LocalDensity.current.fontScale > 1.25f
        Column(Modifier.fillMaxSize().let {
            if (compact) it.verticalScroll(rememberScrollState()) else it
        }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, enabled = !interactionsBlocked,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(GameInk)) {
                    Icon(painterResource(R.drawable.menu_chevron), "Назад", Modifier.size(20.dp).rotate(180f), tint = Color.White)
                }
                Surface(color = GameInk, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f, fill = false)) {
                    Text(state.title, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White,
                        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }

            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AdventureBalances(state.availableBalance, state.savingsBalance,
                    onOpenSavings = savingsAction?.let { open -> { if (state.actionsEnabled && !state.busy && !interactionsBlocked) open() } },
                    modifier = Modifier.widthIn(max = 520.dp).onGloballyPositioned { coordinates ->
                        val visible = coordinates.boundsInWindow()
                        balancesVisible = visible.width >= coordinates.size.width - 1 &&
                            visible.height >= coordinates.size.height - 1 && visible.width > 0 && visible.height > 0
                    })
            }
            Column(Modifier.fillMaxWidth().let { if (compact) it else it.weight(1f).verticalScroll(rememberScrollState()) }
                .padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("На что пойдут\nнаши монеты?", color = Color.White, fontFamily = Rubik,
                        fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 28.sp,
                        modifier = Modifier.semantics { heading() })
                    BoxWithConstraints {
                        val oneColumn = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.4f
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BudgetArticle.entries.chunked(if (oneColumn) 1 else 2).forEach { row ->
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { article ->
                                        PlanArticle(article, state, Modifier.weight(1f).fillMaxHeight(), { info = article.name },
                                            { if (state.actionsEnabled) editing = article.name }, onAmountChange, onAdjust,
                                            interactionsBlocked)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // The adviser is a complete row above the footer, never part of the clipped card viewport.
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
                Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = 104.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).onGloballyPositioned { coordinates ->
                        val visible = coordinates.boundsInWindow()
                        foodNeedVisible = visible.width >= coordinates.size.width - 1 &&
                            visible.height >= coordinates.size.height - 1 && visible.width > 0 && visible.height > 0
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.weight(1f), color = GamePaper, shape = RoundedCornerShape(18.dp)) {
                        Text(buildString {
                            append("На еду до следующей недели ещё нужно ${state.knownNeeds} монет.")
                            if (state.isEditing) {
                                if (state.minimumNeeds > state.knownNeeds) append(" На необходимое оставим хотя бы ${state.minimumNeeds} монет.")
                                if (state.total < state.knownNeeds && state.total > 0) append(" Пока есть только ${state.total} — сохраним их на еду.")
                            }
                        }, Modifier.padding(12.dp), color = GameInk, fontFamily = Nunito,
                            fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 19.sp)
                    }
                    if (characterArt != null) {
                        Canvas(Modifier.width(12.dp).height(18.dp)) {
                            drawPath(Path().apply {
                                moveTo(-1f, 0f)
                                lineTo(size.width, size.height / 2)
                                lineTo(-1f, size.height)
                                close()
                            }, GamePaper)
                        }
                    }
                    }
                    characterArt?.let { art ->
                        Box(Modifier.size(104.dp).align(Alignment.Bottom)) {
                            MovingPetArtwork(art, character?.name, character?.motionIntensity ?: 1f, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            Surface(Modifier.fillMaxWidth(), color = GamePaper, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
                Box(contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(when {
                        state.unallocated > 0 -> "Осталось распределить: ${state.unallocated}"
                        state.isEditing && state.needs < state.minimumNeeds -> "На необходимое нужно ещё ${state.minimumNeeds - state.needs} монет"
                        else -> "Все ${state.total} монет распределены"
                    },
                        color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                    BudgetAllocationSummary(state)
                    val actionEnabled = state.canConfirm
                    Button(onClick = onConfirm,
                        enabled = actionEnabled && !interactionsBlocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AdventureLime, contentColor = GameInk,
                            disabledContainerColor = if (interactionsBlocked && actionEnabled) AdventureLime else Color(0xFFE3DFEC),
                            disabledContentColor = if (interactionsBlocked && actionEnabled) GameInk else Color(0xFF756D8A))) {
                        Text(if (state.isEditing) "Запомнить план" else "Готово",
                            fontFamily = Rubik, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                    Text("Открой копилку, чтобы отложить выбранную сумму.",
                        color = PlanMuted, fontFamily = Nunito, fontSize = 12.sp)
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
            confirmButton = { TextButton(onClick = { info = null }) { Text("Понятно", color = GameInk) } })
    }
    editing?.let { name ->
        val article = BudgetArticle.valueOf(name)
        BudgetAmountDialog(article, state.amount(article), state.amount(article) + state.unallocated,
            onDismiss = { editing = null }, onSave = { onAmountChange(article, it); editing = null },
            minimum = if (article == BudgetArticle.NEEDS) state.minimumNeeds else 0)
    }
}

/** The scale names each allocation; amounts remain in the editable cards above. */
@Composable
private fun BudgetAllocationSummary(state: BudgetUiState) {
    val unallocated = state.unallocated
    val total = state.total
    val assigned = (total - unallocated).coerceAtLeast(0)
    Box(Modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = "Распределение монет"
        stateDescription = BudgetArticle.entries.joinToString(". ") { "${it.title}: ${state.amount(it)} монет" } +
            ". Не распределено: $unallocated монет"
        progressBarRangeInfo = ProgressBarRangeInfo(
            if (total > 0) (assigned.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f, 0f..1f)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE4DFD6))) {
                if (total > 0) {
                    BudgetArticle.entries.forEach { article ->
                        val amount = state.amount(article)
                        if (amount > 0) Box(Modifier.weight((amount.toDouble() / total).toFloat())
                            .fillMaxHeight().background(article.allocationColor))
                    }
                    if (unallocated > 0) Spacer(Modifier.weight((unallocated.toDouble() / total).toFloat()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BudgetArticle.entries.forEach { article ->
                    Text(article.title, Modifier.weight(1f), color = GameInk.copy(alpha = .72f),
                        fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private val BudgetArticle.allocationColor: Color get() = when (this) {
    BudgetArticle.NEEDS -> Color(0xFF829BB3)
    BudgetArticle.WANTS -> Color(0xFFC9A079)
    BudgetArticle.SAVINGS -> Color(0xFF9CAA84)
    BudgetArticle.RESERVE -> Color(0xFFA697BC)
}

@Composable
private fun PlanArticle(article: BudgetArticle, state: BudgetUiState, modifier: Modifier, info: () -> Unit, edit: () -> Unit,
    change: (BudgetArticle, Long) -> Unit, adjust: ((BudgetArticle, Boolean) -> Unit)?, interactionsBlocked: Boolean) {
    val amount = state.amount(article)
    Surface(modifier, color = GamePaper, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxHeight().padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Surface(onClick = info, enabled = !interactionsBlocked, color = Color.Transparent) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    GameArtwork(article.planArt, null, Modifier.size(46.dp), contentScale = ContentScale.Fit)
                    Text(article.title, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    if (article == BudgetArticle.SAVINGS) Text(
                        article.subtitle,
                        color = PlanMuted, fontFamily = Nunito, fontSize = 14.sp, lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlanStep("−", "Уменьшить ${article.title} на 5", state.actionsEnabled && amount - BUDGET_STEP >=
                    (if (article == BudgetArticle.NEEDS) state.minimumNeeds else 0), interactionsBlocked) {
                    if (adjust != null) adjust(article, false) else change(article, amount - BUDGET_STEP)
                }
                Surface(onClick = edit, enabled = state.actionsEnabled && !interactionsBlocked, color = Color.Transparent,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "${article.title}: $amount монет. Ввести сумму" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$amount", color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                            textAlign = TextAlign.Center)
                    }
                }
                val step = minOf(BUDGET_STEP, state.unallocated.coerceAtLeast(0))
                PlanStep("+", "Увеличить ${article.title} на $step", state.actionsEnabled && step > 0, interactionsBlocked) {
                    if (adjust != null) adjust(article, true) else change(article, amount + step)
                }
            }
        }
    }
}

@Composable
private fun PlanStep(label: String, description: String, enabled: Boolean, interactionsBlocked: Boolean, onClick: () -> Unit) {
    TextButton(onClick, Modifier.size(48.dp).semantics { contentDescription = description }, enabled = enabled && !interactionsBlocked,
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = AdventureLime, contentColor = GameInk,
            disabledContainerColor = if (interactionsBlocked && enabled) AdventureLime else Color(0xFFECE6F8),
            disabledContentColor = if (interactionsBlocked && enabled) GameInk else Color(0xFF8C82A6))) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

private val BudgetArticle.planArt: Int @DrawableRes get() = when (this) {
    BudgetArticle.NEEDS -> R.drawable.budget_needs
    BudgetArticle.WANTS -> R.drawable.budget_wants
    BudgetArticle.SAVINGS -> R.drawable.budget_savings
    BudgetArticle.RESERVE -> R.drawable.budget_reserve
}
