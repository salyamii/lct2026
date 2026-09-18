package ru.nksk.lctapp.feature.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.feature.tasks.ui.DeedCard
import ru.nksk.lctapp.feature.tasks.ui.DeedChip
import ru.nksk.lctapp.feature.tasks.ui.DeedColors
import ru.nksk.lctapp.feature.tasks.ui.DeedHeader
import ru.nksk.lctapp.feature.tasks.ui.DeedSheet
import ru.nksk.lctapp.feature.tasks.ui.MemoryGameScreen
import ru.nksk.lctapp.feature.tasks.ui.PriceQuizScreen
import ru.nksk.lctapp.feature.tasks.ui.TargetStopScreen

/** Ключи экранов дел. */
const val DEED_STAR_PLATES = "star_plates"
const val DEED_PRICE_CHECK = "price_check"
const val DEED_TELESCOPE = "telescope"

/**
 * Раздел «Дела»: Смотритель и Гонец просят помощи — за выполненное дело
 * начисляются монеты (демо-награда, баланс пока не подключён).
 */
@Composable
fun DeedsHost(onExit: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("hub") }

    when (screen) {
        DEED_STAR_PLATES -> MemoryGameScreen(
            onBack = { screen = "hub" },
            onFinish = { },
        )
        DEED_PRICE_CHECK -> PriceQuizScreen(
            onBack = { screen = "hub" },
            onFinish = { },
        )
        DEED_TELESCOPE -> TargetStopScreen(
            onBack = { screen = "hub" },
            onFinish = { },
        )
        else -> DeedsScreen(onOpen = { key -> screen = key }, onExit = onExit)
    }
    BackHandler(enabled = screen != "hub") { screen = "hub" }
}

@Composable
private fun DeedsScreen(onOpen: (String) -> Unit, onExit: () -> Unit) {
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
            DeedHeader("Назад", onBack = onExit)
        }
        DeedSheet(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
            Text(
                "Дела",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Сделай дело — получи монеты 🪙",
                fontSize = 13.sp,
                fontFamily = Nunito,
                color = DeedColors.TextSoft,
            )
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DeedCard(
                    title = "Звёздные пласты",
                    description = "Ночью Смотритель снял небо. Разложи пласты по парам созвездий, пока они не перепутались.",
                    rewardLabel = "до 10 монет",
                    scene = painterResource(R.drawable.location_hill),
                    onOpen = { onOpen(DEED_STAR_PLATES) },
                )
                DeedCard(
                    title = "Сверка счетов",
                    description = "Гонец привёз счета из деревенской лавки. Помоги Рыжику найти самую дорогую покупку.",
                    rewardLabel = "до 10 монет",
                    scene = painterResource(R.drawable.location_workshop),
                    onOpen = { onOpen(DEED_PRICE_CHECK) },
                )
                DeedCard(
                    title = "Настрой телескоп",
                    description = "Ловящий сигнал уходит в сторону. Останови бегунок в зелёной зоне — и башня снова найдёт звезду.",
                    rewardLabel = "до 10 монет",
                    scene = painterResource(R.drawable.location_trail),
                    onOpen = { onOpen(DEED_TELESCOPE) },
                )
            }
            Spacer(Modifier.height(14.dp))
            DeedChip("Монеты пока демо — скоро попадут в копилку")
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.height(20.dp))
        }
    }
}

}
