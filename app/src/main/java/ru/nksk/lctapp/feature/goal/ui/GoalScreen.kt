package ru.nksk.lctapp.feature.goal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.*
import ru.nksk.lctapp.core.ui.game.goalItemArtwork
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.Nunito

private val GoalMuted = Color(0xFF635D7B)
private val GoalLine = Color(0xFFE0DCCA)

/** Chapter artwork explains the undertaking; item actions explicitly select a savings goal. */
@Composable
internal fun GoalScreen(
    state: GoalUiState,
    onBack: () -> Unit,
    onAction: (GoalAction) -> Unit,
    onOpenSavings: () -> Unit = {},
    onReturnHome: () -> Unit = onBack,
) {
    val target = state.parts.firstOrNull { it.savingTarget && !it.owned }
    val result = state.purchaseResult
    val ready = state.parts.isNotEmpty() && state.parts.all { it.owned }
    val detailsScroll = rememberScrollState()
    val selectedGoalAnchor = remember { BringIntoViewRequester() }
    val selectionKey = state.goalId to target?.id
    var previousSelection by remember { mutableStateOf(selectionKey) }
    LaunchedEffect(selectionKey) {
        if (selectionKey != previousSelection && previousSelection.first != null) {
            // The selected item changes only after the committed save is projected.
            if (selectionKey.first == previousSelection.first && target != null && state.selected && !state.showList) {
                withFrameNanos { }
                selectedGoalAnchor.bringIntoView()
            } else detailsScroll.scrollTo(0)
        }
        previousSelection = selectionKey
    }
    val back = {
        when {
            result != null -> onAction(GoalAction.DismissPurchaseResult)
            !state.showList && state.returnToList -> onAction(GoalAction.ShowList)
            else -> onBack()
        }
    }
    BackHandler(enabled = result != null) { back() }

    if (state.showList && !state.loading && !state.failed && result == null) {
        GoalProjectList(state, onBack) { onAction(GoalAction.View(it)) }
        return
    }

    AdventureScreen(
        title = "Цели",
        onBack = back,
        backgroundRes = goalPreviewArtwork(state.goalId),
        sceneFraction = .32f,
        sceneAspectRatio = 1.5f,
        contentSpacing = 8.dp,
        pinFooter = false,
        contentScrollState = detailsScroll,
        headerAction = {
            if (!state.loading && !state.failed && result == null) {
                TextButton(onClick = { onAction(GoalAction.ShowList) }, enabled = !state.busy,
                    modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = AdventureNight, contentColor = Color.White)) {
                    Text("Другие цели", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Icon(painterResource(R.drawable.menu_chevron), null, Modifier.padding(start = 4.dp).size(18.dp))
                }
            }
        },
        scene = {},
        footer = {
            when {
                state.loading -> Unit
                state.failed -> AdventurePrimaryButton("Повторить", { onAction(GoalAction.Retry) })
                result != null -> AdventurePrimaryButton(if (ready) "Продолжить историю" else "К целям", {
                    onAction(GoalAction.DismissPurchaseResult)
                    if (ready) onReturnHome()
                }, enabled = !state.busy)
                state.showList -> Unit
                state.canSelect -> AdventurePrimaryButton("Продолжить историю", {
                    onAction(GoalAction.Select(checkNotNull(state.goalId)))
                }, enabled = !state.busy)
                ready || state.completedProject -> AdventurePrimaryButton("Продолжить историю", onReturnHome, enabled = !state.busy)
                state.selected && target != null -> Unit
                state.selected -> GoalSavingsLink(state.balance, onOpenSavings, !state.busy)
            }
        },
    ) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = GameInk)
            state.failed -> {
                AdventureHeading("Не удалось открыть цели")
                AdventureBody("Попробуй загрузить их ещё раз.")
            }
            result != null -> {
                GoalItemIllustration(result.itemId, Modifier.size(88.dp).align(Alignment.CenterHorizontally))
                AdventureHeading("${result.itemTitle} теперь у нас!")
                AdventureBody("Предмет останется в инвентаре.")
                AdventureBody(if (ready) "Всё подготовлено. Продолжим историю!" else "Выбери, на что будем копить дальше.")
            }
            else -> {
                AdventureHeading(state.title)
                AdventureBody(goalPreviewText(state.goalId, state.description))
                when {
                    state.completedProject -> GoalStatus("Цель выполнена")
                    ready -> GoalStatus("Всё подготовлено для продолжения истории")
                    !state.selected -> GoalCaption("Откроется после предыдущей главы")
                }
                if (state.selected && target != null) {
                    GoalSavingProgress(target, state.balance, Modifier.bringIntoViewRequester(selectedGoalAnchor)) {
                        AdventurePrimaryButton("Купить за ${goalPaymentCoins(target.price)}", {
                            onAction(GoalAction.Buy(checkNotNull(state.goalId), target.id))
                        }, enabled = target.canBuy && !state.busy)
                        target.blockedMessage?.let { GoalCaption(it) }
                        AdventureQuietButton("Открыть копилку", onOpenSavings, enabled = !state.busy)
                    }
                }
                Text(if (state.selected && target != null) "Что ещё понадобится" else "Что нужно подготовить", fontFamily = Nunito, fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold, color = GameInk)
                GoalRequirementChoices(state.parts.filterNot { state.selected && it.id == target?.id }, state.selected, !state.busy) { item ->
                    onAction(GoalAction.SelectSavingGoal(checkNotNull(state.goalId), item.id))
                }
                if (state.selected && !state.transfersEnabled && !ready) GoalCaption("Сначала заверши план монет.")
            }
        }
        if (state.confirmation == null) state.message?.let { AdventureBody(it) }
        state.celebration?.let { AdventureBody(it) }
    }
    state.confirmation?.let { GoalPurchaseDialog(it, state.busy, state.message, onAction) }
}

@Composable
private fun GoalRequirementChoices(items: List<GoalPartUiState>, currentChapter: Boolean, enabled: Boolean,
    onChoose: (GoalPartUiState) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 300.dp && fontScale <= 1.3f) 2 else 1
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        GoalRequirementChoice(item, currentChapter, enabled, Modifier.weight(1f).fillMaxHeight()) { onChoose(item) }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GoalRequirementChoice(item: GoalPartUiState, currentChapter: Boolean, enabled: Boolean,
    modifier: Modifier, onChoose: () -> Unit) {
    val target = item.savingTarget && !item.owned
    val shape = RoundedCornerShape(20.dp)
    val canChoose = currentChapter && item.canSelect && !item.owned
    Surface(modifier.clip(shape).selectable(selected = target,
        enabled = enabled && canChoose, role = Role.RadioButton,
        onClick = { if (!target) onChoose() }).semantics {
        contentDescription = "${item.title}, ${goalCoins(item.price)}. " + when {
            item.owned -> "Уже есть"
            target -> "Выбранная цель"
            canChoose -> "Выбрать цель"
            currentChapter -> "Сначала заверши план монет"
            else -> "Пока недоступно"
        }
    }, color = if (target) Color(0xFFF0F7DE) else Color.White, shape = shape,
        border = BorderStroke(if (target) 2.dp else 1.dp, if (target) Color(0xFF89AE44) else GoalLine)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalItemIllustration(item.id, Modifier.size(46.dp))
                Text(goalCoins(item.price), color = GoalMuted, fontFamily = Nunito, fontSize = 14.sp, lineHeight = 18.sp)
            }
            Text(item.title, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 21.sp)
            Surface(color = when {
                target || item.owned -> Color(0xFFE1EDC5)
                canChoose -> Color(0xFFEEEAF7)
                else -> Color.Transparent
            }, shape = RoundedCornerShape(12.dp)) {
                Text(when {
                    item.owned -> "✓ Уже есть"
                    target -> "✓ Выбрано"
                    canChoose -> "Выбрать цель"
                    currentChapter -> "Сначала план монет"
                    else -> "Позже по истории"
                }, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), color = GameInk,
                    fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GoalSavingProgress(part: GoalPartUiState, savings: Long, anchor: Modifier, actions: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GoalLine)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(anchor.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GoalItemIllustration(part.id, Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                GoalCaption("Сейчас собираем")
                Text(part.title, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp, lineHeight = 25.sp)
                GoalCaption("Цена ${goalCoins(part.price)}")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GoalCaption("В копилке")
            Text(goalCoins(savings), color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        LinearProgressIndicator(progress = { if (part.price == 0L) 1f else part.savedCoins.toFloat() / part.price },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(8.dp)).semantics {
                contentDescription = "Для покупки ${part.savedCoins} из ${part.price} монет"
            },
            color = AdventureLime, trackColor = Color(0xFFE3E3CE), drawStopIndicator = {})
        Text(when {
            part.missingCoins != null -> "Для покупки не хватает ${goalMissingCoins(part.missingCoins)}."
            part.availableContribution > 0 -> "Для покупки добавим ${goalPaymentCoins(part.availableContribution)} из бюджета."
            part.remainingCoins > 0 -> "Можно оплатить накоплениями и текущими деньгами."
            else -> "На покупку уже хватает"
        },
            color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp)
        GoalCaption("Копилка общая. При смене цели монеты останутся в ней.")
        actions()
    }
    }
}

@Composable
private fun GoalSavingsLink(balance: Long, onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GoalLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk)) {
        GameArtwork(R.drawable.budget_savings, null, Modifier.size(32.dp))
        Text("В копилке ${goalCoins(balance)}", Modifier.weight(1f).padding(horizontal = 10.dp),
            fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
        Icon(painterResource(R.drawable.menu_chevron), "Открыть копилку", Modifier.size(22.dp))
    }
}

@Composable
private fun GoalPurchaseDialog(confirmation: PurchaseConfirmation, busy: Boolean, message: String?,
    onAction: (GoalAction) -> Unit) {
    val seen = remember(confirmation.contextId) { mutableStateMapOf<String, Boolean>() }
    fun exposure(key: String) = Modifier.onGloballyPositioned { coordinates ->
        val visible = coordinates.boundsInWindow()
        if (visible.width >= coordinates.size.width - 1 && visible.height >= coordinates.size.height - 1 &&
            visible.width > 0 && visible.height > 0) seen[key] = true
    }
    LaunchedEffect(confirmation.contextId, seen.size) {
        // With no savings involved, omit that irrelevant row and do not claim its balance was shown.
        if (listOf("sources", "savings", "available", "food").all { seen[it] == true })
            onAction(GoalAction.ContextPresented(confirmation.contextId))
    }
    AlertDialog(onDismissRequest = { if (!busy) onAction(GoalAction.CancelPurchase) },
        containerColor = GamePaper, titleContentColor = GameInk, textContentColor = GameInk,
        title = { AdventureHeading(confirmation.itemTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoalItemIllustration(confirmation.itemId, Modifier.size(76.dp).align(Alignment.CenterHorizontally))
                Text(confirmation.paymentDescription(), exposure("sources"), color = GameInk,
                    fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp)
                if (confirmation.fromSavings > 0) Text(
                    "В копилке: ${confirmation.savingsBefore} → ${confirmation.remainingSavings}.", exposure("savings"),
                    color = GoalMuted, fontFamily = Nunito, fontSize = 14.sp, lineHeight = 20.sp)
                Text(if (confirmation.availableBefore != confirmation.remainingBalance)
                    "С собой: ${confirmation.availableBefore} → ${goalCoins(confirmation.remainingBalance)}."
                    else "С собой останется ${goalCoins(confirmation.remainingBalance)}.", exposure("available"),
                    color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp)
                Text("На еду до следующей недели нужно ${goalPaymentCoins(confirmation.foodNeeded)}.", exposure("food"),
                    color = GameInk, fontFamily = Nunito, fontSize = 15.sp, lineHeight = 21.sp)
                if (confirmation.foodShortfall > 0) Text(
                    "После покупки на еду не хватит ${goalMissingCoins(confirmation.foodShortfall)}. Всё равно купить?",
                    color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 22.sp)
                message?.let { AdventureBody(it) }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Отмена" to GoalAction.CancelPurchase,
                    (if (message == null) "Купить" else "Повторить") to GoalAction.ConfirmPurchase).forEach { (label, action) ->
                    OutlinedButton(onClick = { onAction(action) }, enabled = !busy,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, GoalMuted),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GameInk)) {
                        Text(label, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        })
}

@Composable
private fun GoalItemIllustration(itemId: String, modifier: Modifier) {
    goalItemArtwork(itemId)?.let { GameArtwork(it, null, modifier, contentScale = ContentScale.Fit) }
}

@Composable
private fun GoalProjectCard(project: GoalProjectUiState, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GoalLine)) {
        Column(Modifier.fillMaxWidth()) {
            GameArtwork(goalPreviewArtwork(project.id), null, Modifier.fillMaxWidth().aspectRatio(1.5f)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdventureHeading(project.title)
                AdventureBody(goalPreviewText(project.id, project.description))
                Text("Что нужно подготовить", color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)
                project.requirements.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(item.title, Modifier.weight(1f), color = GameInk, fontFamily = Nunito, fontSize = 14.sp)
                        Text(if (item.owned) "Есть ✓" else goalCoins(item.price), color = GoalMuted, fontFamily = Nunito, fontSize = 14.sp)
                    }
                }
                if (project.status == GoalProjectStatus.LOCKED) GoalCaption("Откроется после предыдущей главы")
                if (project.status == GoalProjectStatus.COMPLETED) GoalStatus("Цель выполнена")
                AdventurePrimaryButton("Открыть цель", onClick, enabled = enabled)
            }
        }
    }
}

@Composable
private fun GoalProjectList(state: GoalUiState, onBack: () -> Unit, onView: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(AdventureNight).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onBack, Modifier.size(48.dp).background(Color.White.copy(alpha = .10f), CircleShape)) {
                Icon(painterResource(R.drawable.menu_chevron), "Назад", Modifier.size(20.dp).rotate(180f), tint = Color.White)
            }
            Text(if (state.campaignComplete) "Наши приключения" else "Большие цели", color = Color.White,
                fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(GamePaper), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(state.projects, key = { it.id }) { project ->
                GoalProjectCard(project, !state.busy) { onView(project.id) }
            }
        }
    }
}

@Composable
private fun GoalCaption(text: String) {
    Text(text, color = GoalMuted, fontFamily = Nunito, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
private fun GoalStatus(text: String) {
    Text(text, color = Color(0xFF486719), fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
}

private fun goalCoins(amount: Long): String = "$amount " + when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> "монета"
    amount % 10 in 2..4 -> "монеты"
    else -> "монет"
}

private fun goalPreviewText(id: String?, fallback: String): String = when (id) {
    "figma-stargazing-180-v1" -> "Поможем Смотрителям подготовиться к ночным наблюдениям за звёздами."
    "campaign-tower-kit-v1" -> "Подготовимся к исследованию старой северной башни."
    "campaign-researcher-home-v1" -> "Обустроим дом и мастерскую исследователя."
    "campaign-kingdom-map-v1" -> "Подготовим инструменты, чтобы нанести новые пути на карту."
    "campaign-great-expedition-v1" -> "Соберёмся в путешествие за край известной карты."
    else -> fallback
}

private fun goalPreviewArtwork(id: String?): Int = when (id) {
    "campaign-tower-kit-v1" -> R.drawable.goal_preview_tower
    "campaign-researcher-home-v1" -> R.drawable.goal_preview_home
    "campaign-kingdom-map-v1" -> R.drawable.goal_preview_kingdom_map
    "campaign-great-expedition-v1" -> R.drawable.goal_preview_expedition
    else -> R.drawable.goal_preview_stargazing
}
