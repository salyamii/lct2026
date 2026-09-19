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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.AdventureMuted
import ru.nksk.lctapp.core.ui.theme.AdventurePanel

@Composable
internal fun MenuHud(
    state: MainMenuUiState,
    onAction: (MainMenuAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
            MenuText(stringResource(R.string.menu_current_goal), 13, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SavingsPlaceholderBadge() { onAction(MainMenuAction.Coins) }
            LocationEntry { onAction(MainMenuAction.Village) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MenuStatBadge(
                label = stringResource(R.string.menu_hunger),
                value = state.hunger.toString(),
                description = stringResource(R.string.menu_hunger_accessibility, state.hunger),
                modifier = Modifier.weight(1f),
            )
            MenuStatBadge(
                label = stringResource(R.string.menu_fatigue),
                value = state.fatigue.toString(),
                description = stringResource(R.string.menu_fatigue_accessibility, state.fatigue),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SavingsPlaceholderBadge(onClick: () -> Unit) {
    MenuStatBadge(
        label = stringResource(R.string.menu_coins),
        value = stringResource(R.string.menu_value_placeholder),
        description = stringResource(R.string.menu_coins_accessibility),
        onClick = onClick,
        artwork = { MenuArtwork(R.drawable.menu_coin, 27.dp) },
    )
}

@Composable
private fun MenuStatBadge(
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    artwork: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(AdventurePanel.copy(alpha = 0.63f))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        artwork?.invoke()
        Column {
            MenuText(label, 9, color = AdventureMuted)
            MenuText(value, 14)
        }
    }
}

@Composable
private fun LocationEntry(onClick: () -> Unit) {
    val viewConfiguration = LocalViewConfiguration.current
    val pillViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            // This pill's hit area must match its visible bounds, without implicit expansion.
            override val minimumTouchTargetSize = DpSize.Zero
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides pillViewConfiguration) {
        Box(
            Modifier.heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(17.dp))
                    .background(AdventurePanel.copy(alpha = 0.74f))
                    .border(1.dp, Color(0xFFD1CCFF).copy(alpha = 0.24f), RoundedCornerShape(17.dp))
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MenuText(stringResource(R.string.menu_village), 13)
                MenuArtwork(R.drawable.menu_location, 22.dp)
                Image(painterResource(R.drawable.menu_chevron), null, Modifier.size(14.dp))
            }
        }
    }
}
