package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingAnswer
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingComparison
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingItem
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingProgress
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingRound
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingSide

@Composable
fun ShoppingComparisonGameEntry(
    onExit: () -> Unit,
    onCompleted: (ShoppingComparison) -> Unit,
    interactionBlocked: Boolean = false,
    onProgress: (ShoppingProgress) -> Unit = {},
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val model = hiltViewModel<ShoppingComparisonGameViewModel>()
    val game by model.uiState.collectAsStateWithLifecycle()
    val blocked by rememberUpdatedState(interactionBlocked)
    val progress by rememberUpdatedState(onProgress)
    // Restoring the same session re-emits the same IDs; the owner deduplicates by series and round.
    LaunchedEffect(model.seriesId) {
        val restored = model.uiState.value
        if (restored.answers.isNotEmpty()) progress(ShoppingProgress(model.seriesId, restored))
    }
    val onAction: (ShoppingComparisonAction) -> Unit = remember(model) {
        { action ->
            if (!blocked) {
                val answerCount = model.uiState.value.answers.size
                model.onAction(action)
                val accepted = model.uiState.value
                // Emit with the accepted action, even when Back follows before another frame.
                if (accepted.answers.size > answerCount) progress(ShoppingProgress(model.seriesId, accepted))
            }
        }
    }
    val exit = { if (!blocked) onExit() }
    BackHandler(onBack = exit)
    ShoppingComparisonGameScreen(game, onAction, exit, onCompleted, interactionBlocked,
        onHome = { if (homeEnabled) onHome() }, homeEnabled = homeEnabled)
}

@Composable
fun ShoppingComparisonGameScreen(
    game: ShoppingComparison,
    onAction: (ShoppingComparisonAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (ShoppingComparison) -> Unit,
    interactionBlocked: Boolean = false,
    onHome: () -> Unit = onExit,
    homeEnabled: Boolean = !interactionBlocked,
) {
    val round = game.currentRound
    ChapterGameScaffold(
        title = "Покупки для Смотрителя",
        instruction = "Смотритель дал монеты на покупки. Сравни цены в двух лавках.",
        onExit = onExit,
        onHome = onHome,
        homeEnabled = homeEnabled,
        footer = {
            GameActionButton(if (game.completed) "Продолжить" else "Следующая покупка", {
                if (!interactionBlocked) {
                    if (game.completed) onCompleted(game)
                    else onAction(ShoppingComparisonAction.Next)
                }
            }, enabled = game.feedbackShown, interactionBlocked = interactionBlocked)
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cramped = maxHeight < 360.dp
            FinanceWorkbench(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(if (cramped) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (cramped) 6.dp else 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    GamePlaque(Modifier.fillMaxWidth(), light = true, fixedEdges = true,
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FinanceCaption("${game.currentIndex + 1} из ${ShoppingComparison.rounds.size} · ${round.item.label}", fontSize = 17.sp)
                            FinanceCaption(round.item.sameGoods, fontSize = 12.sp)
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShoppingSide.entries.forEach { side ->
                            ShoppingOffer(round, side, game.lastAnswer, !interactionBlocked && !game.feedbackShown,
                                Modifier.weight(1f).fillMaxSize()) { onAction(ShoppingComparisonAction.Choose(side)) }
                        }
                    }
                    GamePlaque(Modifier.fillMaxWidth().height(if (cramped) 72.dp else 88.dp), light = true, fixedEdges = true,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
                        FinanceCaption(game.lastAnswer?.let { shoppingFeedback(round, it) }
                            ?: "Нажми на товар, который выберешь.", fontSize = 14.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        ShoppingComparison.rounds.forEachIndexed { index, order ->
                            val packed = index < game.answers.size
                            Box(Modifier.padding(horizontal = 6.dp).size(if (cramped) 32.dp else 42.dp)
                                .alpha(if (packed) 1f else .3f)
                                .then(if (index == game.currentIndex) Modifier.border(1.dp, Color(0xFFFFD998), RoundedCornerShape(10.dp)) else Modifier)) {
                                GameArtwork(order.item.artwork, if (packed) "${order.item.label}: выбрали" else "${order.item.label}: ещё не выбрали",
                                    Modifier.fillMaxSize().padding(3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingOffer(
    round: ShoppingRound,
    side: ShoppingSide,
    answer: ShoppingAnswer?,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val selected = answer?.selected == side
    val lift by animateFloatAsState(if (selected) .96f else 1f, label = "chosenOffer")
    GamePlaque(modifier.graphicsLayer { scaleX = lift; scaleY = lift }
        .clip(RoundedCornerShape(18.dp))
        .then(if (selected) Modifier.border(2.dp, Color(0xFF79583C), RoundedCornerShape(18.dp)) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Купить за ${round.price(side)}") { onClick() }
        .semantics { if (answer != null) stateDescription = if (selected) "Выбрали" else "Не выбрали" },
        light = true, fixedEdges = true, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 14.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FinanceCaption(if (side == ShoppingSide.LEFT) "Лавка у ворот" else "Лавка у реки", fontSize = 12.sp)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GameArtwork(round.item.artwork, round.item.label,
                    Modifier.size(minOf(maxWidth, maxHeight).coerceAtLeast(1.dp)))
            }
            FinanceCoinAmount(round.price(side))
            FinanceCaption(if (selected) "Выбрали" else "1 штука", fontSize = 12.sp)
        }
    }
}

private fun shoppingFeedback(round: ShoppingRound, answer: ShoppingAnswer): String {
    val difference = kotlin.math.abs(round.leftPrice - round.rightPrice)
    return when {
        difference == 0 -> "Товары и цены одинаковые. Подойдёт любой вариант."
        answer.correct -> "Вещь та же, а цена ниже на $difference ${coinsWord(difference)}. Остаток вернём Смотрителю."
        else -> "В другой лавке такая же вещь дешевле на $difference ${coinsWord(difference)}."
    }
}

private fun coinsWord(amount: Int): String = when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1 -> "монету"
    amount % 10 in 2..4 -> "монеты"
    else -> "монет"
}

private val ShoppingItem.label: String get() = when (this) {
    ShoppingItem.MAP -> "Карта"
    ShoppingItem.LANTERN -> "Фонарь"
    ShoppingItem.LENS -> "Линза"
}

private val ShoppingItem.sameGoods: String get() = when (this) {
    ShoppingItem.MAP -> "Две одинаковые карты. Нужна одна."
    ShoppingItem.LANTERN -> "Фонари светят одинаково. Нужен один."
    ShoppingItem.LENS -> "Обе линзы подходят. Нужна одна."
}

private val ShoppingItem.artwork: Int get() = when (this) {
    ShoppingItem.MAP -> R.drawable.deed_goods_map
    ShoppingItem.LANTERN -> R.drawable.deed_goods_lantern
    ShoppingItem.LENS -> R.drawable.deed_goods_lens
}
