package ru.nksk.lctapp.feature.menu.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.theme.AdventureLabel
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.AdventurePanel
import ru.nksk.lctapp.core.ui.theme.Rubik
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun MenuActions(onAction: (MainMenuAction) -> Unit, viewport: DpSize, state: MainMenuUiState, backgroundPainter: Painter, modifier: Modifier = Modifier) {
    var panelPosition by remember { mutableStateOf(Offset.Zero) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.dayStatus?.let { status ->
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(AdventurePanel.copy(alpha = 0.63f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = AdventureLabel,
                fontFamily = Rubik,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        }
        state.notice?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = AdventureLabel, fontFamily = Rubik) }
        if (state.canFeed) GameActionButton(
            text = state.mealPrice?.let { "Покормить за $it монет" } ?: "Покормить",
            onClick = { onAction(MainMenuAction.Feed) },
            modifier = Modifier.padding(horizontal = 18.dp),
            interactionBlocked = state.busy,
            minHeight = ButtonDefaults.MinHeight,
            shape = ButtonDefaults.shape,
            textStyle = MaterialTheme.typography.labelLarge,
            contentPadding = ButtonDefaults.ContentPadding,
            containerColor = AdventureLime, contentColor = AdventureNight,
        )
        Box(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(34.dp))
                .clip(RoundedCornerShape(34.dp))
                .onGloballyPositioned { panelPosition = it.positionInRoot() }
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(34.dp)),
        ) {
            FrostedVillagePanel(viewport, panelPosition, Modifier.matchParentSize(), backgroundPainter)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                QuickAction(
                    R.drawable.menu_gear, R.string.menu_gear,
                    Modifier.weight(1f), tilt = 1.2f, topPadding = 8,
                    onClick = { onAction(MainMenuAction.Gear) },
                )
                QuickAction(
                    R.drawable.menu_tasks, R.string.menu_tasks,
                    Modifier.weight(1f), tilt = -0.6f, topPadding = 0,
                    onClick = { onAction(MainMenuAction.Tasks) },
                )
                QuickAction(
                    R.drawable.menu_goal, R.string.menu_goal,
                    Modifier.weight(1f), tilt = -1.2f, topPadding = 5,
                    onClick = { onAction(MainMenuAction.Goal) },
                )
            }
        }
        state.spendingPreview?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = AdventureLabel, fontFamily = Rubik) }
        GameActionButton(
            text = if (state.canRestartCampaign) "Вернуться к началу истории" else state.continueLabel ?: stringResource(R.string.menu_continue),
            onClick = { onAction(if (state.canRestartCampaign) MainMenuAction.CampaignArchive else MainMenuAction.ContinueDay) },
            interactionBlocked = state.busy,
            modifier = Modifier.padding(horizontal = 18.dp)
                .shadow(12.dp, RoundedCornerShape(30.dp)),
            minHeight = 60.dp,
            shape = RoundedCornerShape(30.dp),
            contentPadding = ButtonDefaults.ContentPadding,
            containerColor = AdventureLime, contentColor = AdventureNight,
        )
    }
}

@Composable
private fun QuickAction(
    @DrawableRes image: Int,
    @StringRes label: Int,
    modifier: Modifier,
    tilt: Float,
    topPadding: Int,
    onClick: () -> Unit,
) {
    val title = stringResource(label)
    // The whole illustration is a touch target, including the small caption underneath.
    val extraLabelHeight = (16f * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)).dp
    Box(
        modifier.heightIn(min = 98.dp + extraLabelHeight)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(top = topPadding.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Image(
            painterResource(R.drawable.menu_star), null,
            Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp).size(7.dp),
        )
        // Keep label and artwork overlapping as in the exported component.
        MenuText(
            title, 11, color = AdventureNight,
            modifier = Modifier.padding(top = 64.dp)
                .graphicsLayer { rotationZ = tilt }
                .widthIn(min = 78.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AdventureLabel.copy(alpha = 0.96f))
                .border(1.dp, Color(0xFFB0A1EB).copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
        )
        MenuArtwork(image, 78.dp)
    }
}
