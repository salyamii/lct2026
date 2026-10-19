package ru.nksk.lctapp.feature.storygames.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.domain.minigame.chapterone.TripPacking
import ru.nksk.lctapp.domain.minigame.chapterone.TripSupply

@Composable
fun TripPackingGameEntry(
    onExit: () -> Unit,
    onCompleted: (TripPacking) -> Unit,
    interactionBlocked: Boolean = false,
) {
    val model = hiltViewModel<TripPackingGameViewModel>()
    val game by model.uiState.collectAsStateWithLifecycle()
    val blocked by rememberUpdatedState(interactionBlocked)
    val onAction: (TripPackingAction) -> Unit = remember(model) { { if (!blocked) model.onAction(it) } }
    val exit = { if (!blocked) onExit() }
    BackHandler(onBack = exit)
    TripPackingGameScreen(game, onAction, exit, onCompleted, interactionBlocked)
}

@Composable
fun TripPackingGameScreen(
    game: TripPacking,
    onAction: (TripPackingAction) -> Unit,
    onExit: () -> Unit,
    onCompleted: (TripPacking) -> Unit,
    interactionBlocked: Boolean = false,
) {
    ChapterGameScaffold(
        title = "Сборы в дорогу",
        instruction = "Возьми карту и фонарь. Оставь 3 монеты про запас.",
        onExit = onExit,
        footer = {
            GameActionButton(if (game.completed) "Продолжить" else "Собрать рюкзак", {
                if (!interactionBlocked && game.ready) {
                    val finished = game.finish()
                    onAction(TripPackingAction.Finish)
                    onCompleted(finished)
                }
            }, enabled = game.ready, interactionBlocked = interactionBlocked)
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cramped = maxHeight < 380.dp
            val small = maxHeight < 445.dp
            val cardHeight = if (cramped) 76.dp else if (small) 88.dp else 108.dp
            FinanceWorkbench(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(if (cramped) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (cramped) 3.dp else if (small) 4.dp else 7.dp)) {
                    GamePlaque(Modifier.fillMaxWidth().height(if (cramped) 43.dp else 51.dp), light = true, fixedEdges = true,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            FinanceCaption("Монеты Смотрителя", fontSize = 13.sp)
                            FinanceCoinAmount(TripPacking.BUDGET)
                        }
                    }
                    SupplyShelf(TripSupply.entries.filter { it.required }, game, cardHeight,
                        !interactionBlocked, onAction)
                    FinanceCaption("По желанию", Modifier.fillMaxWidth(), color = Color(0xFFFFE8B9), fontSize = 12.sp)
                    SupplyShelf(TripSupply.entries.filterNot { it.required }, game, cardHeight,
                        !interactionBlocked, onAction)
                    if (cramped) GamePlaque(Modifier.fillMaxWidth().height(52.dp), light = true, fixedEdges = true,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            FinanceCaption("В рюкзаке: ${game.selected.size}", fontSize = 12.sp)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FinanceCaption("Про запас", fontSize = 12.sp)
                                FinanceCoinAmount(game.reserve)
                            }
                        }
                    } else Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                            GameArtwork(R.drawable.story_supply_bag, null, Modifier.fillMaxSize())
                            FinanceCaption(if (game.selected.isEmpty()) "Рюкзак пуст" else "В рюкзаке: ${game.selected.size}",
                                Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xEFFFEDC6)).padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 12.sp)
                        }
                        GamePlaque(Modifier.weight(1.15f), light = true, fixedEdges = true,
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                FinanceCaption("Про запас", fontSize = 13.sp)
                                FinanceCoinAmount(game.reserve)
                                FinanceCaption(when {
                                    game.reserve < TripPacking.MINIMUM_RESERVE -> "Оставь хотя бы 3 монеты"
                                    !game.essentialsPacked -> "Нужны карта и фонарь"
                                    else -> "Можно отправляться"
                                }, fontSize = 12.sp)
                            }
                        }
                    }
                    if (!cramped) FinanceCaption("Нажми на вещь, чтобы взять или убрать.", Modifier.fillMaxWidth(),
                        color = Color(0xFFFFE8B9), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SupplyShelf(
    supplies: List<TripSupply>,
    game: TripPacking,
    height: Dp,
    enabled: Boolean,
    onAction: (TripPackingAction) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        supplies.forEach { supply ->
            val selected = supply in game.selected
            val available = enabled && !game.completed && (selected || supply.price <= game.reserve)
            val scale by animateFloatAsState(if (selected) .94f else 1f, label = "packedSupply")
            GamePlaque(Modifier.weight(1f).height(height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .alpha(if (available || selected) 1f else .5f)
                .clip(RoundedCornerShape(13.dp))
                .then(if (selected) Modifier.border(2.dp, Color(0xFF79583C), RoundedCornerShape(13.dp)) else Modifier)
                .clickable(enabled = available, role = Role.Checkbox, onClickLabel = if (selected) "Убрать" else "Взять") {
                    onAction(TripPackingAction.Toggle(supply))
                }.semantics { stateDescription = if (selected) "В рюкзаке" else "На столе" },
                light = true, fixedEdges = true, contentPadding = PaddingValues(horizontal = 7.dp, vertical = 7.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    GameArtwork(supply.artwork, null, Modifier.size(if (height < 80.dp) 26.dp else if (height < 100.dp) 34.dp else 50.dp))
                    FinanceCaption(supply.label, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameArtwork(R.drawable.menu_coin, null, Modifier.size(16.dp))
                        FinanceCaption(supply.price.toString(), fontSize = 13.sp)
                        if (selected) FinanceCaption(" · Взяли", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private val TripSupply.label: String get() = when (this) {
    TripSupply.MAP -> "Карта"
    TripSupply.LANTERN -> "Фонарь"
    TripSupply.BUN -> "Булочка"
    TripSupply.KEYCHAIN -> "Брелок"
    TripSupply.TOY_BOAT -> "Лодочка"
}

private val TripSupply.artwork: Int @DrawableRes get() = when (this) {
    TripSupply.MAP -> R.drawable.deed_goods_map
    TripSupply.LANTERN -> R.drawable.deed_goods_lantern
    TripSupply.BUN -> R.drawable.prop_bakery_bun
    TripSupply.KEYCHAIN -> R.drawable.prop_fair_compass_keychain
    TripSupply.TOY_BOAT -> R.drawable.prop_fair_toy_boat
}
