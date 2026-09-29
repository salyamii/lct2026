package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameLoadingIndicator
import ru.nksk.lctapp.core.ui.components.MealChoices
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import ru.nksk.lctapp.core.ui.components.gameScene
import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.AdventureHeading
import ru.nksk.lctapp.core.ui.components.AdventurePrimaryButton
import ru.nksk.lctapp.core.ui.components.AdventureQuietButton
import ru.nksk.lctapp.core.ui.components.GamePaper

@Composable
fun DeedsScreen(onTraining: () -> Unit, onExit: () -> Unit,
    state: DeedsUiState = DeedsUiState(), onStart: (String) -> Unit = {}, onRetry: () -> Unit = {},
    onFeed: (String) -> Unit = {}, onCurrentEvent: () -> Unit = {},
    onDismissMessage: () -> Unit = {},
) {
    BackHandler(enabled = state.busy) {}
    Box(Modifier.fillMaxSize()) {
        DeedsContent(
            state = state.copy(busy = false),
            onTraining = { if (!state.busy) onTraining() },
            onExit = { if (!state.busy) onExit() },
            onStart = { if (!state.busy) onStart(it) },
            onRetry = { if (!state.busy) onRetry() },
            onCurrentEvent = { if (!state.busy) onCurrentEvent() },
        )
        if (state.busy) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) {
            GameLoadingIndicator(Modifier.align(Alignment.Center).safeDrawingPadding())
        }
    }
    // The selected card can be far below the list heading. Always show the
    // rejected action beside the player, without resetting their scroll position.
    if (state.message != null && !state.busy) AlertDialog(
        onDismissRequest = onDismissMessage,
        containerColor = GamePaper,
        title = { AdventureHeading("Пока не можем начать дело") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AdventureBody(state.message)
                MealChoices(state.meals, false, onFeed)
            }
        },
        confirmButton = {
            if (state.hasCurrentEvent) AdventurePrimaryButton("Вернуться к событию", {
                onDismissMessage()
                onCurrentEvent()
            }) else AdventureQuietButton("Понятно", onDismissMessage)
        },
        dismissButton = {
            if (state.hasCurrentEvent) AdventureQuietButton("Остаться в делах", onDismissMessage)
        },
    )
}

@Composable
private fun DeedsContent(onTraining: () -> Unit, onExit: () -> Unit,
    state: DeedsUiState, onStart: (String) -> Unit, onRetry: () -> Unit,
    onCurrentEvent: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            GameArtwork(
                R.drawable.location_observatory,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(230.dp),
                contentScale = ContentScale.Crop,
            )
            DeedHeader(stringResource(R.string.navigation_back), onBack = onExit)
        }
        DeedSheet(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("deeds_list"),
            ) {
                item(key = "heading", contentType = "heading") {
                    Column {
                        Text(
                            stringResource(R.string.menu_tasks),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = Rubik,
                            color = DeedColors.Text,
                        )
                        Spacer(Modifier.height(2.dp))
                        if (state.loading) GameLoadingIndicator()
                        if (state.failed) {
                            Text("Не удалось загрузить дела.", color = DeedColors.Text)
                            Button(onRetry) { Text("Повторить") }
                        }
                        if (state.hasCurrentEvent) OutlinedButton(onCurrentEvent, enabled = !state.busy) { Text("Вернуться к событию") }
                        if (!state.loading && !state.failed && state.offers.isEmpty()) {
                            Text("Пока нет новых дел. Продолжи день, чтобы узнать, кому нужна помощь.", color = DeedColors.TextSoft)
                        }
                    }
                }
                item(key = "skill-training", contentType = "training") {
                    Spacer(Modifier.height(16.dp))
                    Surface(onClick = onTraining, enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(), color = Color(0xFFF0F5DF),
                        shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0xFFD1DCAF))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GameArtwork(R.drawable.menu_tasks, null, Modifier.size(58.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Тренировка навыков", color = DeedColors.Text, fontFamily = Rubik,
                                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Учимся планировать, копить и выбирать", color = DeedColors.TextSoft,
                                    fontFamily = Nunito, fontSize = 14.sp)
                            }
                            Text("›", color = DeedColors.Text, fontSize = 28.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                items(state.offers, key = { "offer-${it.id}" }, contentType = { "deed" }) { offer ->
                    Spacer(Modifier.height(12.dp))
                    DeedCard(
                        title = offer.title,
                        description = "${offer.description}\n${offer.effort}",
                        rewardLabel = offer.reward,
                        deadline = offer.deadline,
                        scene = gameScene(offer.scene),
                        onOpen = { if (!state.busy) onStart(offer.id) },
                    )
                }
            }
        }
    }
}
