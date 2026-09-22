package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import ru.nksk.lctapp.core.ui.components.gameScene
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

enum class DeedsAction { StarPlates, PriceCheck, Telescope }

@Composable
fun DeedsScreen(onOpen: (DeedsAction) -> Unit, onExit: () -> Unit,
    state: DeedsUiState = DeedsUiState(), onStart: (String) -> Unit = {}, onRetry: () -> Unit = {},
    onFeed: (String) -> Unit = {}, onCurrentEvent: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeedColors.Scene),
    ) {
        Box {
            Image(
                painterResource(R.drawable.location_observatory),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(230.dp),
                contentScale = ContentScale.Crop,
            )
            DeedHeader(stringResource(R.string.navigation_back), onBack = onExit)
        }
        DeedSheet(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
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
                    Button({ onFeed(meal.id) }, enabled = meal.enabled && !state.busy) { Text(meal.label) }
                }
                if (!state.loading && !state.failed && state.offers.isEmpty()) {
                    Text("Пока нет предложенных дел. Продолжи день, чтобы встретить новые поручения.", color = DeedColors.TextSoft)
                }
                state.offers.forEach { offer ->
                    Spacer(Modifier.height(12.dp))
                    DeedCard(
                        title = offer.title,
                        description = "${offer.description}\n${offer.effort}",
                        rewardLabel = offer.reward,
                        deadline = offer.deadline,
                        scene = gameScene(offer.scene)?.let { painterResource(it) }
                            ?: androidx.compose.ui.graphics.painter.ColorPainter(DeedColors.Cream),
                        onOpen = { if (!state.busy) onStart(offer.id) },
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("Мини-игры · тренировка", fontFamily = Rubik, fontWeight = FontWeight.Bold, color = DeedColors.Text)
                Text(
                    stringResource(R.string.deeds_subtitle),
                    fontSize = 13.sp,
                    fontFamily = Nunito,
                    color = DeedColors.TextSoft,
                )
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DeedCard(
                        title = stringResource(R.string.deeds_star_title),
                        description = stringResource(R.string.deeds_star_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = painterResource(R.drawable.location_hill),
                        onOpen = { onOpen(DeedsAction.StarPlates) },
                    )
                    DeedCard(
                        title = stringResource(R.string.deeds_price_title),
                        description = stringResource(R.string.deeds_price_description, state.petName),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = painterResource(R.drawable.location_workshop),
                        onOpen = { onOpen(DeedsAction.PriceCheck) },
                    )
                    DeedCard(
                        title = stringResource(R.string.deeds_target_title),
                        description = stringResource(R.string.deeds_target_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = painterResource(R.drawable.location_trail),
                        onOpen = { onOpen(DeedsAction.Telescope) },
                    )
                }
                Spacer(Modifier.height(14.dp))
                DeedChip(stringResource(R.string.deeds_demo_notice))
                Spacer(Modifier.height(16.dp))
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
