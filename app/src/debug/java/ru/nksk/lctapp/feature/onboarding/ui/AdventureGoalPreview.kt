package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.accessoryArtwork
import ru.nksk.lctapp.app.customizationArtwork
import ru.nksk.lctapp.app.onboardingGoalOptions
import ru.nksk.lctapp.data.game.content.STARS_GOAL
import ru.nksk.lctapp.data.game.content.TOWER_GOAL
import ru.nksk.lctapp.data.game.content.HOME_GOAL

private val previewGoals = onboardingGoalOptions()

@Composable
private fun AdventureGoalDesign(started: Boolean = false, selected: String? = STARS_GOAL,
    fur: CharacterFur = CharacterFur.Copper, accessory: OnboardingAccessory = OnboardingAccessory.None) {
    var selectedId by rememberSaveable { mutableStateOf(selected) }
    var showStarted by rememberSaveable { mutableStateOf(started) }
    val artwork = customizationArtwork()
    MaterialTheme {
        if (showStarted) {
            AdventureStartedScreen(artwork, previewGoals.first { it.id == selectedId },
                accessoryArtwork(artwork).portraits.getValue(accessory).getValue(fur),
                onBack = { showStarted = false }, onContinue = {})
        } else {
            AdventureGoalSelectionScreen(artwork.copy(background = R.drawable.location_hill_day), previewGoals, selectedId,
                onSelect = { selectedId = it }, onBack = {}, onConfirm = { showStarted = true })
        }
    }
}

@Preview(name = "01 · Выбор цели · Телефон", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun GoalSelectionPhonePreview() = AdventureGoalDesign()

@Preview(name = "02 · Приключение началось · Телефон", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun AdventureStartedPhonePreview() = AdventureGoalDesign(started = true)

@Preview(name = "03 · Цель не выбрана", widthDp = 360, heightDp = 740, showBackground = true)
@Composable
private fun GoalSelectionEmptyPreview() = AdventureGoalDesign(selected = null)

@Preview(name = "04 · Выбор · Крупный текст", widthDp = 360, heightDp = 740, fontScale = 1.5f, showBackground = true)
@Composable
private fun GoalSelectionLargeTextPreview() = AdventureGoalDesign()

@Preview(name = "05 · Начало · Крупный текст", widthDp = 360, heightDp = 740, fontScale = 1.5f, showBackground = true)
@Composable
private fun AdventureStartedLargeTextPreview() = AdventureGoalDesign(started = true)

@Preview(name = "06 · Выбор · Альбомный", widthDp = 891, heightDp = 411, showBackground = true)
@Composable
private fun GoalSelectionLandscapePreview() = AdventureGoalDesign()

@Preview(name = "07 · Начало · Альбомный", widthDp = 891, heightDp = 411, showBackground = true)
@Composable
private fun AdventureStartedLandscapePreview() = AdventureGoalDesign(started = true)

@Preview(name = "08 · Дом · Планшет", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun AdventureStartedTabletPreview() = AdventureGoalDesign(started = true, selected = HOME_GOAL,
    fur = CharacterFur.Sand, accessory = OnboardingAccessory.Bandana)

@Preview(name = "09 · Башня · Другой образ", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun AdventureStartedTowerPreview() = AdventureGoalDesign(started = true, selected = TOWER_GOAL,
    fur = CharacterFur.Russet, accessory = OnboardingAccessory.Backpack)
