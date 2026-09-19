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

enum class DeedsAction(val kind: ru.nksk.lctapp.domain.minigame.MiniGameKind) {
    StarPlates(ru.nksk.lctapp.domain.minigame.MiniGameKind.MEMORY),
    PriceCheck(ru.nksk.lctapp.domain.minigame.MiniGameKind.PRICE_QUIZ),
    Telescope(ru.nksk.lctapp.domain.minigame.MiniGameKind.TELESCOPE),
}

@Composable
fun DeedsScreen(onOpen: (DeedsAction) -> Unit, onExit: () -> Unit, state: DeedsUiState, onRetry: () -> Unit) {
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
                Text(
                    stringResource(R.string.deeds_subtitle),
                    fontSize = 13.sp,
                    fontFamily = Nunito,
                    color = DeedColors.TextSoft,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.deeds_resources, state.hunger, state.fatigue),
                    color = DeedColors.Text, fontFamily = Nunito,
                )
                if (state.loading) Text(stringResource(R.string.game_loading), color = DeedColors.Text)
                if (state.error) {
                    Text(stringResource(R.string.game_load_error), color = DeedColors.Text)
                    DeedButton(stringResource(R.string.game_retry), onRetry)
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DeedCard(
                        title = stringResource(R.string.deeds_star_title),
                        description = stringResource(R.string.deeds_star_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = painterResource(R.drawable.location_hill),
                        onOpen = { onOpen(DeedsAction.StarPlates) },
                        costLabel = stringResource(R.string.deeds_cost, DeedsAction.StarPlates.kind.hungerCost, DeedsAction.StarPlates.kind.fatigueCost),
                        enabled = DeedsAction.StarPlates.kind in state.available,
                        unavailableLabel = if (state.loading || state.error) null else stringResource(R.string.deeds_unavailable),
                    )
                    DeedCard(
                        title = stringResource(R.string.deeds_price_title),
                        description = stringResource(R.string.deeds_price_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = painterResource(R.drawable.location_workshop),
                        onOpen = { onOpen(DeedsAction.PriceCheck) },
                        costLabel = stringResource(R.string.deeds_cost, DeedsAction.PriceCheck.kind.hungerCost, DeedsAction.PriceCheck.kind.fatigueCost),
                        enabled = DeedsAction.PriceCheck.kind in state.available,
                        unavailableLabel = if (state.loading || state.error) null else stringResource(R.string.deeds_unavailable),
                    )
                    DeedCard(
                        title = stringResource(R.string.deeds_target_title),
                        description = stringResource(R.string.deeds_target_description),
                        rewardLabel = stringResource(R.string.deeds_demo_max),
                        scene = painterResource(R.drawable.location_trail),
                        onOpen = { onOpen(DeedsAction.Telescope) },
                        costLabel = stringResource(R.string.deeds_cost, DeedsAction.Telescope.kind.hungerCost, DeedsAction.Telescope.kind.fatigueCost),
                        enabled = DeedsAction.Telescope.kind in state.available,
                        unavailableLabel = if (state.loading || state.error) null else stringResource(R.string.deeds_unavailable),
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
