package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.core.ui.components.GameArtwork
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** Шапка экрана дела: пилюля «‹ Название» поверх ночной сцены. */
@Composable
fun DeedHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val backDescription = stringResource(R.string.navigation_back)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onBack)
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .semantics { contentDescription = backDescription },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    color = DeedColors.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Rubik,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    color = DeedColors.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Rubik,
                )
            }
        }
        trailing()
    }
}

/** Янтарный или лаймовый чип события. */
@Composable
fun DeedChip(text: String, modifier: Modifier = Modifier, lime: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (lime) DeedColors.Lime else DeedColors.Chip)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = DeedColors.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Nunito,
        )
    }
}

/** Чип цены с монетой из каталога ассетов. */
@Composable
fun CoinChip(text: String, modifier: Modifier = Modifier, lime: Boolean = false) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (lime) DeedColors.Lime else DeedColors.Chip)
            .padding(start = 8.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameArtwork(R.drawable.menu_coin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            color = DeedColors.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Nunito,
        )
    }
}

/** Лаймовая пилюля-действие. */
@Composable
fun DeedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(DeedColors.Lime)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = DeedColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Rubik,
        )
    }
}

/** Белая пилюля-действие (вторичный выбор). */
@Composable
fun DeedButtonSoft(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(DeedColors.CreamCard)
            .border(1.dp, DeedColors.Border, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = DeedColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Rubik,
        )
    }
}

/** Кремовый лист события с закруглённым верхом поверх ночной сцены. */
@Composable
fun DeedSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(DeedColors.Cream)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        content()
    }
}

/**
 * Карточка дела в списке: арт-сцена сверху, кремовый лист с описанием,
 * чипами и лаймовой кнопкой — как event-карточки в макетах.
 */
@Composable
fun DeedCard(
    title: String,
    description: String,
    rewardLabel: String,
    @DrawableRes scene: Int?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    deadline: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DeedColors.Cream)
            .clickable(onClick = onOpen),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            scene?.let { GameArtwork(
                it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            ) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
            )
            CoinChip(
                rewardLabel,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp, top = 12.dp)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = Nunito,
                color = DeedColors.Text.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(12.dp))
            DeedButton(stringResource(R.string.deeds_start), onOpen)
            deadline?.let {
                Spacer(Modifier.height(8.dp))
                DeedChip(it)
            }
        }
    }
}

/** Итоговый лист дела: награда и кнопки повтора/выхода. */
@Composable
fun DeedResultSheet(
    emoji: String,
    title: String,
    reward: Int,
    onAgain: () -> Unit,
    onHub: () -> Unit,
) {
    Dialog(onDismissRequest = onHub) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(28.dp))
                .background(DeedColors.Cream)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Rubik,
                color = DeedColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            CoinChip(stringResource(R.string.deeds_demo_reward, reward), lime = true)
            Spacer(Modifier.height(20.dp))
            DeedButton(stringResource(R.string.deeds_again), onAgain)
            Spacer(Modifier.height(10.dp))
            DeedButtonSoft(stringResource(R.string.deeds_back_to_list), onHub)
        }
    }
}
