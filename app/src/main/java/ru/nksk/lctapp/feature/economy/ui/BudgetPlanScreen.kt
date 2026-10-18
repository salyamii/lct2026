package ru.nksk.lctapp.feature.economy.ui

import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.game.paymentCoinAmount

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.game.toLiveAdventurePetPresentation
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.domain.pet.PetState

private val PlanMuted = Color(0xFF715E45)

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
    onAdviceShown: (String) -> Unit = {},
    onArticleFocused: (BudgetArticle) -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var balancesVisible by remember(state.availableBalance, state.savingsBalance) { mutableStateOf(false) }
    // A read food requirement stays known across draft edits, but never crosses another financial context.
    var foodNeedVisible by remember(state.planningId, state.knownNeeds, state.availableBalance, state.savingsBalance) {
        mutableStateOf(false)
    }
    var transferVisible by remember(contextId) { mutableStateOf(false) }
    val savingsAction = onOpenSavings ?: onDeposit ?: onWithdraw
    val character = pet?.toLiveAdventurePetPresentation()
    val characterArt = character?.artworkRes
    LaunchedEffect(contextId, balancesVisible, foodNeedVisible, transferVisible, state.savingsTransfer) {
        if (balancesVisible && foodNeedVisible && (state.savingsTransfer == 0L || transferVisible)) {
            contextId?.let { onContextPresented?.invoke(it) }
        }
    }
    Box(Modifier.fillMaxSize().background(GameInk)) {
        GameArtwork(R.drawable.location_city_evening, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(GameInk.copy(alpha = .42f),
            Color.Transparent, GameInk.copy(alpha = .12f)))))
        AdaptiveActionPanel(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(0.dp),
            contentSpacing = 0.dp,
            actionSpacing = 0.dp,
            sectionSpacing = 0.dp,
            fillBody = true,
            minimumBodyHeight = 176.dp * LocalDensity.current.fontScale.coerceAtLeast(1f),
            actions = {
                BudgetAdviser(state, characterArt, character?.name, character?.motionIntensity ?: 1f, onAdviceShown)
                BudgetPlanFooter(state, interactionsBlocked, onConfirm, contextId) { transferVisible = it }
            },
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameBackButton(onClick = onBack, enabled = !interactionsBlocked)
                GameSceneTitle(state.title, Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                key(state.availableBalance, state.savingsBalance) {
                    BudgetBalances(state.availableBalance, state.savingsBalance,
                        onOpenSavings = savingsAction?.let { open ->
                            { if (state.actionsEnabled && !state.busy && !interactionsBlocked) open() }
                        },
                        modifier = Modifier.widthIn(max = 520.dp).onGloballyPositioned { coordinates ->
                            val visible = coordinates.boundsInWindow()
                            balancesVisible = visible.width >= coordinates.size.width - 1 &&
                                visible.height >= coordinates.size.height - 1 && visible.width > 0 && visible.height > 0
                        })
                }
            }
            state.foodReminderText?.let { reminder ->
                key(state.planningId, reminder) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center) {
                        Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), color = GamePaper,
                            shape = RoundedCornerShape(14.dp)) {
                            Text(reminder, Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("budget.plan.food").onGloballyPositioned { coordinates ->
                                    val visible = coordinates.boundsInWindow()
                                    if (visible.width >= coordinates.size.width - 1 &&
                                        visible.height >= coordinates.size.height - 1 && visible.width > 0 && visible.height > 0) {
                                        foodNeedVisible = true
                                    }
                                }, color = PlanMuted, fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("На что пойдут монеты?", color = Color.White, fontFamily = Rubik,
                        fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, lineHeight = 26.sp,
                        modifier = Modifier.semantics { heading() })
                    BoxWithConstraints {
                        val oneColumn = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.4f
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BudgetArticle.entries.chunked(if (oneColumn) 1 else 2).forEach { row ->
                                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { article ->
                                        PlanArticle(article, state, Modifier.weight(1f).fillMaxHeight(),
                                            { onArticleFocused(article) },
                                            { if (state.actionsEnabled && !interactionsBlocked) {
                                                onArticleFocused(article); editing = article.name
                                            } }, onAmountChange, onAdjust, interactionsBlocked)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { name ->
        val article = BudgetArticle.valueOf(name)
        BudgetAmountDialog(article, state.amount(article), state.amount(article) + state.unallocated,
            onDismiss = { editing = null }, onSave = { onArticleFocused(article); onAmountChange(article, it); editing = null },
            minimum = if (article == BudgetArticle.NEEDS) state.minimumNeeds else 0)
    }
}

@Composable
private fun BudgetAdviser(state: BudgetUiState, @DrawableRes characterArt: Int?, name: String?,
    motionIntensity: Float, onAdviceShown: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp),
        contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(min = 96.dp)
            .testTag("budget.plan.adviser"), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.weight(1f), color = GamePaper, shape = RoundedCornerShape(18.dp)) {
                    val advice = state.adviser
                    FinancialAdviceText(advice.text, Modifier.padding(12.dp), exposureKey = advice.id,
                        onShown = { onAdviceShown(advice.id) })
                }
                if (characterArt != null) Canvas(Modifier.width(12.dp).height(18.dp)) {
                    drawPath(Path().apply {
                        moveTo(-1f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(-1f, size.height)
                        close()
                    }, GamePaper)
                }
            }
            characterArt?.let { art ->
                Box(Modifier.size(88.dp).align(Alignment.Bottom)) {
                    MovingPetArtwork(art, name, motionIntensity, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun BudgetPlanFooter(state: BudgetUiState, interactionsBlocked: Boolean, onConfirm: () -> Unit,
    contextId: String?, onTransferVisibility: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("budget.plan.footer"), color = GamePaper,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when {
                    state.unallocated > 0 -> "Распредели ещё ${state.unallocated}"
                    state.isEditing && state.needs < state.minimumNeeds ->
                        "Добавь в «Нужно» ещё ${paymentCoinAmount(state.minimumNeeds - state.needs)}"
                    else -> "Распределено: ${coinAmount(state.total)}"
                }, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                BudgetAllocationSummary(state)
                if (state.savingsTransfer > 0) key(contextId, state.savingsTransfer) {
                    Column(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
                        val visible = coordinates.boundsInWindow()
                        onTransferVisibility(visible.width >= coordinates.size.width - 1 &&
                            visible.height >= coordinates.size.height - 1 && visible.width > 0 && visible.height > 0)
                    }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("В копилку перейдёт ${paymentCoinAmount(state.savingsTransfer)}",
                            color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("После подтверждения: доступно ${state.availableAfterConfirmation}, в копилке ${state.savingsAfterConfirmation}.",
                            color = PlanMuted, fontFamily = Nunito, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                GameActionButton(if (state.isEditing) "Подтвердить бюджет" else "Готово", onConfirm,
                    enabled = state.canConfirm, interactionBlocked = interactionsBlocked,
                    modifier = Modifier.testTag("budget.plan.confirm"))
            }
        }
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
        stateDescription = BudgetArticle.entries.joinToString(". ") { "${it.title}: ${state.amount(it)}" } +
            ". Не распределено: $unallocated"
        progressBarRangeInfo = ProgressBarRangeInfo(
            if (total > 0) (assigned.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f, 0f..1f)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                        Box(Modifier.size(6.dp).background(article.allocationColor, CircleShape))
                        Text(article.title, color = GameInk.copy(alpha = .72f),
                            fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
                    }
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
private fun PlanArticle(article: BudgetArticle, state: BudgetUiState, modifier: Modifier, focus: () -> Unit, edit: () -> Unit,
    change: (BudgetArticle, Long) -> Unit, adjust: ((BudgetArticle, Boolean) -> Unit)?, interactionsBlocked: Boolean) {
    val amount = state.amount(article)
    GamePlaque(modifier.testTag("budget.article.${article.name}"), artworkRes = R.drawable.menu_action_plaque_v2, fixedEdges = true,
        contentPadding = PaddingValues(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 22.dp)) {
        Column(Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()
                .testTag("budget.article.${article.name}.heading")
                .heightIn(min = 68.dp).clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !interactionsBlocked, role = Role.Button, onClickLabel = "Объяснить ${article.title}", onClick = focus)
                .semantics { selected = state.focusedArticle == article }) {
                GameArtwork(article.planArt, null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                Text(article.title, color = if (state.focusedArticle == article) Color(0xFF925420) else GameInk,
                    fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 22.sp)
            }
            Row(Modifier.fillMaxWidth().testTag("budget.article.${article.name}.controls"),
                verticalAlignment = Alignment.CenterVertically) {
                PlanStep("−", "Уменьшить ${article.title} на 5", state.actionsEnabled && amount - BUDGET_STEP >=
                    (if (article == BudgetArticle.NEEDS) state.minimumNeeds else 0), interactionsBlocked) {
                    focus()
                    if (adjust != null) adjust(article, false) else change(article, amount - BUDGET_STEP)
                }
                Surface(onClick = edit, enabled = state.actionsEnabled && !interactionsBlocked, color = Color.Transparent,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "${article.title}: ${coinAmount(amount)}. Ввести сумму" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$amount", color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                            textAlign = TextAlign.Center)
                    }
                }
                val step = minOf(BUDGET_STEP, state.unallocated.coerceAtLeast(0))
                PlanStep("+", "Увеличить ${article.title} на $step", state.actionsEnabled && step > 0, interactionsBlocked) {
                    focus()
                    if (adjust != null) adjust(article, true) else change(article, amount + step)
                }
            }
        }
    }
}

@Composable
private fun PlanStep(label: String, description: String, enabled: Boolean, interactionsBlocked: Boolean, onClick: () -> Unit) {
    GamePlaque(Modifier.size(48.dp).alpha(if (enabled) 1f else .45f),
        artworkRes = R.drawable.menu_action_plaque_v2, fixedEdges = true, fillVisibleHeight = true,
        contentPadding = PaddingValues(0.dp)) {
        TextButton(onClick, Modifier.fillMaxSize().semantics { contentDescription = description },
            enabled = enabled && !interactionsBlocked,
            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(containerColor = Color.Transparent, contentColor = GameInk,
                disabledContainerColor = Color.Transparent, disabledContentColor = GameInk)) {
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BudgetBalances(available: Long, savings: Long, onOpenSavings: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BudgetBalance("Доступно", available, Modifier.weight(1f))
        BudgetBalance("В копилке", savings, Modifier.weight(1f), onOpenSavings)
    }
}

@Composable
private fun BudgetBalance(label: String, amount: Long, modifier: Modifier, onClick: (() -> Unit)? = null) {
    GamePlaque(modifier.heightIn(min = 68.dp).then(if (onClick == null) Modifier else
        Modifier.clickable(role = Role.Button, onClickLabel = "Открыть копилку", onClick = onClick)),
        artworkRes = R.drawable.menu_wallet_panel_v3, fixedEdges = true,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = PlanMuted, fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, lineHeight = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                GameArtwork(R.drawable.menu_coin, null, Modifier.size(20.dp))
                Text("$amount", color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, lineHeight = 22.sp)
            }
        }
    }
}

private val BudgetArticle.planArt: Int @DrawableRes get() = when (this) {
    BudgetArticle.NEEDS -> R.drawable.budget_needs
    BudgetArticle.WANTS -> R.drawable.budget_wants
    BudgetArticle.SAVINGS -> R.drawable.budget_savings
    BudgetArticle.RESERVE -> R.drawable.budget_reserve
}
