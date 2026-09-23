package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.OnboardingBudgetArtwork
import ru.nksk.lctapp.app.customizationArtwork
import ru.nksk.lctapp.app.onboardingGoalOptions
import ru.nksk.lctapp.data.game.content.STARS_GOAL
import ru.nksk.lctapp.data.game.content.TOWER_GOAL
import ru.nksk.lctapp.data.game.content.HOME_GOAL

private val previewGoals = onboardingGoalOptions()

@Preview(name = "Вступление · Телефон", widthDp = 402, heightDp = 874, showBackground = true)
@Preview(name = "Вступление · Крупный текст", widthDp = 360, heightDp = 740, fontScale = 1.5f, showBackground = true)
@Preview(name = "Вступление · Альбомный", widthDp = 891, heightDp = 411, showBackground = true)
@Composable
private fun AdventureGoalBriefingPreview() {
    MaterialTheme {
        AdventureGoalBriefingScreen(
            artwork = customizationArtwork().copy(background = R.drawable.location_hill_day),
            onBack = {}, onContinue = {},
        )
    }
}

@Composable
private fun AdventureGoalDesign(selected: String? = STARS_GOAL) {
    var selectedId by rememberSaveable { mutableStateOf(selected) }
    var showBudget by rememberSaveable { mutableStateOf(false) }
    val artwork = customizationArtwork().copy(background = R.drawable.location_hill_day)
    MaterialTheme {
        if (showBudget) {
            BudgetIntroductionScreen(artwork,
                categoryArtwork = { category, modifier -> OnboardingBudgetArtwork(category, modifier) },
                onBack = { showBudget = false }, onContinue = {})
        } else {
            AdventureGoalSelectionScreen(artwork, previewGoals, selectedId,
                onSelect = { selectedId = it }, onBack = {}, onConfirm = { showBudget = true })
        }
    }
}

@Preview(name = "01 · Выбор цели · Телефон", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun GoalSelectionPhonePreview() = AdventureGoalDesign()

@Preview(name = "03 · Цель не выбрана", widthDp = 360, heightDp = 740, showBackground = true)
@Composable
private fun GoalSelectionEmptyPreview() = AdventureGoalDesign(selected = null)

@Preview(name = "04 · Выбор · Крупный текст", widthDp = 360, heightDp = 740, fontScale = 1.5f, showBackground = true)
@Composable
private fun GoalSelectionLargeTextPreview() = AdventureGoalDesign()

@Preview(name = "06 · Выбор · Альбомный", widthDp = 891, heightDp = 411, showBackground = true)
@Composable
private fun GoalSelectionLandscapePreview() = AdventureGoalDesign()

@Preview(name = "08 · Дом · Планшет", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun GoalSelectionTabletPreview() = AdventureGoalDesign(selected = HOME_GOAL)

@Preview(name = "09 · Выбор башни", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun GoalSelectionTowerPreview() = AdventureGoalDesign(selected = TOWER_GOAL)
