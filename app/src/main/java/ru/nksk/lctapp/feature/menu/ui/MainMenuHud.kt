package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.AdventureLavender
import ru.nksk.lctapp.core.ui.theme.AdventureLabel
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureMuted
import ru.nksk.lctapp.core.ui.theme.AdventurePanel
import ru.nksk.lctapp.core.ui.theme.Rubik

@Composable
internal fun MenuHud(
    petName: String,
    coins: Long,
    completedGoals: Int,
    totalGoals: Int,
    onAction: (MainMenuAction) -> Unit,
    goalTitle: String = "Выбрать большую цель",
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(AdventurePanel.copy(alpha = 0.63f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(24.dp))
                .clickable(role = Role.Button) { onAction(MainMenuAction.Goal) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MenuText(stringResource(R.string.menu_goal_label), 11, color = AdventureLavender, letterSpacing = 0.44f)
            MenuText(goalTitle, 13, modifier = Modifier.weight(1f))
            if (totalGoals > 0) MenuText(
                stringResource(R.string.menu_goal_progress, completedGoals, totalGoals),
                size = 11,
                color = AdventureLime,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoinsBadge(coins) { onAction(MainMenuAction.Coins) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = petName,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .background(AdventurePanel.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = AdventureLabel,
                    fontFamily = Rubik,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CoinsBadge(coins: Long, onClick: () -> Unit) {
    val description = stringResource(R.string.menu_coins_accessibility, coins)
    Row(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(AdventurePanel.copy(alpha = 0.63f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuArtwork(R.drawable.menu_coin, 27.dp)
        Column {
            MenuText(stringResource(R.string.menu_coins), 9, color = AdventureMuted)
            MenuText(coins.toString(), 14)
        }
    }
}

@Composable
internal fun VillageMapButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.menu_map),
        contentDescription = stringResource(R.string.menu_village),
        contentScale = ContentScale.Fit,
        modifier = modifier.alpha(0.92f).clickable(role = Role.Button, onClick = onClick),
    )
}
