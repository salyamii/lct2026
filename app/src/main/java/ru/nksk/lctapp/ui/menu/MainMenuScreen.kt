package ru.nksk.lctapp.ui.menu

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.AdventureLabel
import ru.nksk.lctapp.core.ui.theme.AdventureLavender
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureMuted
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.core.ui.theme.AdventurePanel
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik

/** Menu actions are placeholders until the game flows are implemented. */
enum class MainMenuAction {
    Gear, Tasks, Goal, Coins, Village, ContinueDay,
}

@Composable
fun MainMenuScreen(
    modifier: Modifier = Modifier,
    coins: Int = 100,
    completedGoals: Int = 0,
    totalGoals: Int = 4,
    onAction: (MainMenuAction) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AdventureNight)) {
        val viewport = DpSize(maxWidth, maxHeight)
        VillageBackdrop()
        // Only the foreground observes insets: the village extends behind native system bars.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 12.dp)) {
            val useSideBySide = maxWidth > maxHeight || maxWidth >= 840.dp
            val sidePanelWidth = 360.dp.coerceAtMost(maxWidth * 0.52f)
            if (useSideBySide) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CharacterScene(Modifier.weight(1f).fillMaxSize())
                    Column(
                        Modifier.width(sidePanelWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        MenuHud(coins, completedGoals, totalGoals, onAction)
                        MenuActions(onAction, viewport)
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = 480.dp).fillMaxSize().align(Alignment.TopCenter),
                ) {
                    MenuHud(coins, completedGoals, totalGoals, onAction)
                    CharacterScene(Modifier.weight(1f).fillMaxWidth())
                    MenuActions(onAction, viewport)
                }
            }
        }
    }
}

@Composable
private fun VillageBackdrop() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Figma crops a 483 × 858 image at x=-72 in a 390 × 844 frame.
        val scale = maxOf(maxWidth / 390.dp, maxHeight / 844.dp)
        Image(
            painter = painterResource(R.drawable.menu_village),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.align(Alignment.Center)
                .offset(x = (-25.5f * scale).dp, y = (7f * scale).dp)
                .requiredSize((483f * scale).dp, (858f * scale).dp)
                .blur(1.5.dp),
        )
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF090B21).copy(alpha = 0.18f)))
}

@Composable
private fun MenuHud(
    coins: Int,
    completedGoals: Int,
    totalGoals: Int,
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
            MenuText(stringResource(R.string.menu_goal_label), 11, color = AdventureLavender, letterSpacing = 0.44f)
            MenuText(stringResource(R.string.menu_current_goal), 13, modifier = Modifier.weight(1f))
            MenuText(
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
            LocationEntry { onAction(MainMenuAction.Village) }
        }
    }
}

@Composable
private fun CoinsBadge(coins: Int, onClick: () -> Unit) {
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

@Composable
private fun CharacterScene(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val characterSize = minOf(maxWidth * 1.18f, maxHeight * 0.92f, 560.dp)
        Image(
            painterResource(R.drawable.menu_ground_shadow), null,
            Modifier.offset(x = characterSize * -0.03f, y = characterSize * 0.378f)
                .size(characterSize * 0.328f, characterSize * 0.117f),
        )
        Image(
            painterResource(R.drawable.menu_ryzhik),
            contentDescription = stringResource(R.string.menu_fox_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.requiredSize(characterSize),
        )
    }
}

@Composable
private fun MenuActions(onAction: (MainMenuAction) -> Unit, viewport: DpSize) {
    var panelPosition by remember { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(34.dp))
                .clip(RoundedCornerShape(34.dp))
                .onGloballyPositioned { panelPosition = it.positionInRoot() }
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(34.dp)),
        ) {
            FrostedVillagePanel(viewport, panelPosition, Modifier.matchParentSize())
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
        Button(
            onClick = { onAction(MainMenuAction.ContinueDay) },
            modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth().heightIn(min = 60.dp)
                .shadow(12.dp, RoundedCornerShape(30.dp)),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AdventureLime, contentColor = AdventureNight),
        ) {
            Text(
                stringResource(R.string.menu_continue),
                fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Reuses the same village crop behind the glass; no network or screenshot capture needed. */
@Composable
private fun FrostedVillagePanel(viewport: DpSize, position: Offset, modifier: Modifier) {
    val village = ImageBitmap.imageResource(R.drawable.menu_village)
    Box(modifier) {
        Box(
            Modifier.matchParentSize().blur(9.dp).drawWithCache {
                val scale = maxOf(viewport.width / 390.dp, viewport.height / 844.dp)
                val artworkWidth = (483f * scale).dp.toPx()
                val artworkHeight = (858f * scale).dp.toPx()
                val left = viewport.width.toPx() / 2f - artworkWidth / 2f - (25.5f * scale).dp.toPx()
                val top = viewport.height.toPx() / 2f - artworkHeight / 2f + (7f * scale).dp.toPx()
                onDrawBehind {
                    drawImage(
                        village,
                        dstOffset = IntOffset((left - position.x).roundToInt(), (top - position.y).roundToInt()),
                        dstSize = IntSize(artworkWidth.roundToInt(), artworkHeight.roundToInt()),
                    )
                    drawRect(Color(0xFF090B21).copy(alpha = 0.18f))
                }
            },
        )
        Box(Modifier.matchParentSize().background(Color(0xFF130E30).copy(alpha = 0.30f)))
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

@Composable
private fun MenuText(
    text: String,
    size: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    letterSpacing: Float = 0f,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text, modifier = modifier, color = color,
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
        fontSize = size.sp, lineHeight = (size + 4).sp,
        letterSpacing = letterSpacing.sp, textAlign = textAlign,
    )
}

@Preview(name = "Figma · 390 × 844", widthDp = 390, heightDp = 844)
@Preview(name = "Compact phone", widthDp = 360, heightDp = 640)
@Preview(name = "Landscape", widthDp = 844, heightDp = 390)
@Preview(name = "Large text", widthDp = 390, heightDp = 844, fontScale = 1.5f)
@Composable
private fun MainMenuPreview() {
    LCTAppTheme { MainMenuScreen(onAction = {}) }
}
