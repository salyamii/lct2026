package ru.nksk.lctapp.feature.menu.ui

import ru.nksk.lctapp.core.ui.components.tour.spotlightTarget

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.AdventureLabel
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.core.ui.theme.Nunito

private val ContinueButtonCornerRadius = 30.dp
private val QuickActionCenterLift = 8.dp

private object QuickActionsPanelShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rise = minOf(with(density) { QuickActionCenterLift.toPx() }, size.height * .15f)
        val corner = minOf(with(density) { 32.dp.toPx() }, size.height * .45f, size.width * .18f)
        val middle = size.width / 2f
        val shoulderSpan = middle - corner
        return Outline.Generic(Path().apply {
            moveTo(0f, rise + corner)
            quadraticBezierTo(0f, rise, corner, rise)
            cubicTo(corner + shoulderSpan * .38f, rise,
                middle - shoulderSpan * .38f, 0f, middle, 0f)
            cubicTo(middle + shoulderSpan * .38f, 0f,
                size.width - corner - shoulderSpan * .38f, rise, size.width - corner, rise)
            quadraticBezierTo(size.width, rise, size.width, rise + corner)
            lineTo(size.width, size.height - corner)
            quadraticBezierTo(size.width, size.height, size.width - corner, size.height)
            lineTo(corner, size.height)
            quadraticBezierTo(0f, size.height, 0f, size.height - corner)
            close()
        })
    }
}

@Composable
internal fun MenuActions(onAction: (MainMenuAction) -> Unit, state: MainMenuUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.notice?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = AdventureLabel, fontFamily = Rubik) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .shadow(8.dp, QuickActionsPanelShape, clip = false)
                .background(Brush.verticalGradient(listOf(
                    Color(0xFF87768C).copy(alpha = .48f),
                    Color(0xFF382D4A).copy(alpha = .52f),
                    AdventureNight.copy(alpha = .66f),
                )), QuickActionsPanelShape)
                .border(1.dp, Brush.verticalGradient(listOf(
                    Color(0xFFF6E4C6).copy(alpha = .42f),
                    Color(0xFFB5A4D8).copy(alpha = .12f),
                    Color(0xFFB5A4D8).copy(alpha = .24f),
                )), QuickActionsPanelShape)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            QuickAction(
                R.drawable.menu_action_gear_v2, R.string.menu_gear,
                Modifier.weight(1f).spotlightTarget("menu.gear"),
                onClick = { onAction(MainMenuAction.Gear) },
            )
            QuickAction(
                R.drawable.menu_action_tasks_v4, R.string.menu_tasks,
                Modifier.weight(1f).padding(bottom = QuickActionCenterLift).spotlightTarget("menu.tasks"),
                onClick = { onAction(MainMenuAction.Tasks) },
            )
            QuickAction(
                R.drawable.menu_action_goal_v2, R.string.menu_goal,
                Modifier.weight(1f).spotlightTarget("menu.goal")
                    .semantics { contentDescription = "Цель: ${state.goalTitle}" },
                onClick = { onAction(MainMenuAction.Goal) },
                badge = if (state.totalGoals > 0) stringResource(R.string.menu_goal_progress, state.completedGoals, state.totalGoals) else null,
            )
        }
        state.spendingPreview?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = AdventureLabel, fontFamily = Rubik) }
        MenuPlaqueButton(
            text = if (state.canRestartCampaign) "Вернуться к началу истории" else state.continueLabel ?: stringResource(R.string.menu_continue),
            onClick = { onAction(if (state.canRestartCampaign) MainMenuAction.CampaignArchive else MainMenuAction.ContinueDay) },
            interactionBlocked = state.busy,
            modifier = Modifier.padding(horizontal = 18.dp).spotlightTarget("menu.continue", cornerRadius = ContinueButtonCornerRadius, gap = 4.dp),
            minHeight = 64.dp,
        )
    }
}

@Composable
private fun MenuPlaqueButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    interactionBlocked: Boolean,
    minHeight: Dp,
) {
    GamePlaque(modifier.fillMaxWidth(), light = true, contentPadding = PaddingValues(0.dp)) {
        GameActionButton(
            text = text,
            onClick = onClick,
            interactionBlocked = interactionBlocked,
            minHeight = maxOf(minHeight, 72.dp),
            shape = RoundedCornerShape(ContinueButtonCornerRadius),
            textStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp, lineHeight = 18.sp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
            containerColor = Color.Transparent,
            contentColor = GameInk,
            artwork = false,
        )
    }
}

@Composable
private fun QuickAction(
    @DrawableRes image: Int,
    @StringRes label: Int,
    modifier: Modifier,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val title = stringResource(label)
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier.heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        val artSize = maxWidth.coerceAtMost(112.dp)
        val largeText = fontScale > 1.3f || (artSize < 100.dp && fontScale > 1.15f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(artSize)) {
                GameArtwork(image, null, Modifier.matchParentSize())
                if (!largeText) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = artSize * .73f)
                            .height(artSize * .17f).padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(title, color = AdventureNight, fontFamily = Nunito,
                            fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                            lineHeight = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            // Large accessibility text can wrap without being clipped by the painted plaque.
            if (largeText) {
                Text(title, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(AdventureLabel).padding(horizontal = 4.dp, vertical = 3.dp),
                    color = AdventureNight, fontFamily = Nunito,
                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                    lineHeight = 16.sp, textAlign = TextAlign.Center)
            }
        }
        badge?.let {
            MenuText(it, 10, color = AdventureNight,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp)
                    .clip(RoundedCornerShape(20.dp)).background(AdventureLime)
                    .padding(horizontal = 7.dp, vertical = 4.dp))
        }
    }
}
