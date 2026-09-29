package ru.nksk.lctapp.feature.menu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import ru.nksk.lctapp.core.ui.components.GameArtwork
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.AdventureLavender
import ru.nksk.lctapp.core.ui.theme.AdventureLabel
import ru.nksk.lctapp.core.ui.theme.AdventureLime
import ru.nksk.lctapp.core.ui.theme.AdventureMuted
import ru.nksk.lctapp.core.ui.theme.AdventurePanel
import ru.nksk.lctapp.core.ui.theme.Rubik
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.core.ui.components.GamePaper
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

private val MenuBadgeMinHeight = 56.dp

@Composable
internal fun MenuHud(
    petName: String,
    coins: Long,
    completedGoals: Int,
    totalGoals: Int,
    onAction: (MainMenuAction) -> Unit,
    goalTitle: String = "Выбрать большую цель",
    modifier: Modifier = Modifier,
    budget: MenuBudgetUiState? = null,
    budgetExpanded: Boolean = false,
    onBudgetExpandedChange: (Boolean) -> Unit = {},
    settingsButton: (@Composable () -> Unit)? = null,
    onRename: () -> Unit = {},
    renameEnabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Progress is informational; the bottom menu owns the goal action.
            Row(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(AdventurePanel.copy(alpha = 0.63f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            settingsButton?.let { button ->
                Box(
                    Modifier.size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AdventurePanel.copy(alpha = 0.63f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) { button() }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (budget == null) CoinsBadge(coins) { onAction(MainMenuAction.Coins) }
            else BudgetBadge(budget, budgetExpanded, onBudgetExpandedChange,
                onClick = { onAction(MainMenuAction.Coins) }, onFinance = { onAction(MainMenuAction.Finance) },
                onSavings = { onAction(MainMenuAction.Savings) })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier.heightIn(min = MenuBadgeMinHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AdventurePanel.copy(alpha = 0.85f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                        .clickable(enabled = renameEnabled, role = Role.Button,
                            onClickLabel = "Изменить имя", onClick = onRename)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = petName,
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
}

@Composable
private fun BudgetBadge(
    budget: MenuBudgetUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onFinance: (() -> Unit)? = null,
    onSavings: (() -> Unit)? = null,
) {
    val arrowRotation = animateFloatAsState(
        targetValue = if (expanded) 270f else 90f,
        animationSpec = tween(200),
        label = "Budget dropdown rotation",
    )
    val openBudgetLabel = stringResource(R.string.menu_budget_open)
    // Only the header participates in HUD measurement. The menu is a separate popup,
    // so expanding it cannot resize the pet scene or reposition the map/actions.
    Box {
        Row(
            Modifier.widthIn(min = 164.dp).heightIn(min = MenuBadgeMinHeight)
                .clip(RoundedCornerShape(20.dp))
                .background(AdventurePanel.copy(alpha = 0.9f))
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = openBudgetLabel, onClick = onClick)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameArtwork(R.drawable.menu_coin, null, Modifier.size(26.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(8.dp))
            Column {
                MenuText(stringResource(R.string.menu_budget_title), 11, color = AdventureMuted)
                Text(budget.available.toString(), color = AdventureLabel, fontFamily = Rubik,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    painter = painterResource(R.drawable.menu_chevron),
                    contentDescription = stringResource(if (expanded) R.string.menu_budget_collapse else R.string.menu_budget_expand),
                    tint = AdventureLabel.copy(alpha = .8f),
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = arrowRotation.value },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width((LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceIn(0.dp, 280.dp)),
            shape = RoundedCornerShape(24.dp),
            containerColor = GamePaper,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
        ) {
            BudgetPopupSummary(budget)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = GameInk.copy(alpha = .10f))
            BudgetPopupAction("Открыть бюджет") { onExpandedChange(false); onClick() }
            if (onSavings != null) BudgetPopupAction("Открыть копилку") { onExpandedChange(false); onSavings() }
            if (onFinance != null) BudgetPopupAction("История приключения") { onExpandedChange(false); onFinance() }
        }
    }
}

@Preview(name = "Монетки · свернуто", showBackground = true, backgroundColor = 0xFF120F30)
@Composable
private fun CollapsedBudgetBadgePreview() {
    LCTAppTheme { BudgetBadge(MenuBudgetUiState(25, 0, 66, 20), false, {}, {}) }
}

@Preview(name = "Монетки · раскрыто", showBackground = true, backgroundColor = 0xFF120F30)
@Preview(name = "Монетки · крупный текст", fontScale = 1.5f, showBackground = true, backgroundColor = 0xFF120F30)
@Composable
private fun ExpandedBudgetBadgePreview() {
    LCTAppTheme { BudgetBadge(MenuBudgetUiState(25, 0, 66, 20), true, {}, {}, {}, {}) }
}

@Composable
private fun BudgetPopupSummary(budget: MenuBudgetUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        if (LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BudgetBalance("Доступно", budget.available, R.drawable.menu_coin)
                BudgetBalance("В копилке", budget.actualSavings, R.drawable.budget_savings)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BudgetBalance("Доступно", budget.available, R.drawable.menu_coin, Modifier.weight(1f))
                BudgetBalance("В копилке", budget.actualSavings, R.drawable.budget_savings, Modifier.weight(1f))
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = GameInk.copy(alpha = .10f))
        Text("В бюджете", color = GameInk.copy(alpha = .65f), fontFamily = Nunito,
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        BudgetArticleRow("Нужно", budget.needs)
        BudgetArticleRow("Хочу", budget.wants)
        BudgetArticleRow("В копилку", budget.savings)
        BudgetArticleRow("Запас", budget.reserve)
        if (budget.unallocated > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Осталось распределить: ${budget.unallocated}", color = GameInk.copy(alpha = .72f),
                fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun BudgetBalance(title: String, amount: Long, artwork: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = GameInk.copy(alpha = .7f), fontFamily = Nunito,
            fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GameArtwork(artwork, null, Modifier.size(26.dp), contentScale = ContentScale.Fit)
            Text(amount.toString(), color = GameInk, fontFamily = Rubik,
                fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
    }
}

@Composable
private fun BudgetArticleRow(title: String, amount: Long) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.weight(1f), color = GameInk, fontFamily = Nunito,
            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(amount.toString(), color = GameInk, fontFamily = Nunito,
            fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}

@Composable
private fun BudgetPopupAction(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, color = GameInk, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) },
        trailingIcon = { Icon(painterResource(R.drawable.menu_chevron), null, Modifier.size(16.dp), tint = GameInk.copy(alpha = .55f)) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun CoinsBadge(coins: Long, onClick: () -> Unit) {
    val description = stringResource(R.string.menu_coins_accessibility, coins)
    Row(
        Modifier.heightIn(min = MenuBadgeMinHeight).clip(RoundedCornerShape(20.dp))
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
    GameArtwork(R.drawable.menu_map,
        contentDescription = stringResource(R.string.menu_village),
        contentScale = ContentScale.Fit,
        modifier = modifier.alpha(0.92f).clickable(role = Role.Button, onClick = onClick),
    )
}
