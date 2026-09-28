package ru.nksk.lctapp.feature.tasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
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

enum class DeedsAction {
    StarPlates, PriceCheck, Telescope, SkillTraining,
    Lights, Sequence, Sliding, Pipes, Sorting, Differences, Stacking,
}

@Composable
fun DeedsScreen(onOpen: (DeedsAction) -> Unit, onExit: () -> Unit,
    state: DeedsUiState = DeedsUiState(), onStart: (String) -> Unit = {}, onRetry: () -> Unit = {},
    onFeed: (String) -> Unit = {}, onCurrentEvent: () -> Unit = {},
) {
    BackHandler(enabled = state.busy) {}
    Box(Modifier.fillMaxSize()) {
        DeedsContent(
            state = state.copy(busy = false),
            onOpen = { if (!state.busy) onOpen(it) },
            onExit = { if (!state.busy) onExit() },
            onStart = { if (!state.busy) onStart(it) },
            onRetry = { if (!state.busy) onRetry() },
            onFeed = { if (!state.busy) onFeed(it) },
            onCurrentEvent = { if (!state.busy) onCurrentEvent() },
        )
        if (state.busy) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }) {
            LinearProgressIndicator(Modifier.align(Alignment.TopCenter).safeDrawingPadding().fillMaxWidth())
        }
    }
}

@Composable
private fun DeedsContent(onOpen: (DeedsAction) -> Unit, onExit: () -> Unit,
    state: DeedsUiState, onStart: (String) -> Unit, onRetry: () -> Unit,
    onFeed: (String) -> Unit, onCurrentEvent: () -> Unit,
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
                        if (state.loading) CircularProgressIndicator()
                        if (state.failed) {
                            Text("Не удалось загрузить дела.", color = DeedColors.Text)
                            Button(onRetry) { Text("Повторить") }
                        }
                        state.message?.let { Text(it, color = DeedColors.Text) }
                        if (state.hasCurrentEvent) OutlinedButton(onCurrentEvent, enabled = !state.busy) { Text("Вернуться к событию") }
                        state.meals.forEach { meal ->
                            meal.spending?.let { Text(it) }
                            meal.consequence?.let { Text(it, color = DeedColors.Text) }
                            Button({ onFeed(meal.id) }, enabled = meal.enabled && !state.busy) { Text(meal.label) }
                        }
                        if (!state.loading && !state.failed && state.offers.isEmpty()) {
                            Text("Пока нет предложенных дел. Продолжи день, чтобы встретить новые поручения.", color = DeedColors.TextSoft)
                        }
                    }
                }
                item(key = "skill-training", contentType = "training") {
                    Spacer(Modifier.height(16.dp))
                    Surface(onClick = { onOpen(DeedsAction.SkillTraining) }, enabled = !state.busy,
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
                item(key = "training-heading", contentType = "heading") {
                    Column {
                        Spacer(Modifier.height(24.dp))
                        Text("Тренировка в мини-играх", fontFamily = Rubik, fontWeight = FontWeight.Bold, color = DeedColors.Text)
                        Text(
                            stringResource(R.string.deeds_subtitle),
                            fontSize = 13.sp,
                            fontFamily = Nunito,
                            color = DeedColors.TextSoft,
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
                item(key = "training-stars", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_star_title),
                        description = stringResource(R.string.deeds_star_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_hill,
                        onOpen = { onOpen(DeedsAction.StarPlates) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-prices", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_price_title),
                        description = stringResource(R.string.deeds_price_description, state.petName),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_workshop,
                        onOpen = { onOpen(DeedsAction.PriceCheck) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-telescope", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_target_title),
                        description = stringResource(R.string.deeds_target_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_trail,
                        onOpen = { onOpen(DeedsAction.Telescope) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-lights", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_lights_title),
                        description = stringResource(R.string.deeds_lights_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_observatory,
                        onOpen = { onOpen(DeedsAction.Lights) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-sequence", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_sequence_title),
                        description = stringResource(R.string.deeds_sequence_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_observatory,
                        onOpen = { onOpen(DeedsAction.Sequence) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-sliding", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_sliding_title),
                        description = stringResource(R.string.deeds_sliding_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_trail,
                        onOpen = { onOpen(DeedsAction.Sliding) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-pipes", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_pipes_title),
                        description = stringResource(R.string.deeds_pipes_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_workshop,
                        onOpen = { onOpen(DeedsAction.Pipes) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-sorting", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_sorting_title),
                        description = stringResource(R.string.deeds_sorting_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_pier,
                        onOpen = { onOpen(DeedsAction.Sorting) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-differences", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_differences_title),
                        description = stringResource(R.string.deeds_differences_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_observatory,
                        onOpen = { onOpen(DeedsAction.Differences) },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                item(key = "training-stacking", contentType = "deed") {
                    DeedCard(
                        title = stringResource(R.string.deeds_stacking_title),
                        description = stringResource(R.string.deeds_stacking_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = R.drawable.location_pier,
                        onOpen = { onOpen(DeedsAction.Stacking) },
                    )
                }
                item(key = "training-note", contentType = "footer") {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        DeedChip(stringResource(R.string.deeds_demo_notice))
                        Spacer(Modifier.height(16.dp))
                        Spacer(Modifier.height(20.dp))
                    }
                }

            }
        }
    }
}
