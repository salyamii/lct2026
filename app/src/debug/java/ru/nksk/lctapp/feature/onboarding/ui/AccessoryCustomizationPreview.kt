package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.app.customizationArtwork
import ru.nksk.lctapp.app.accessoryArtwork

@Preview(name = "01 · Телефон · Рюкзак", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun AccessoryBackpackPreview() = AccessoryDesignPreview()

@Preview(name = "02 · Телефон · Песочный с банданой", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun AccessoryBandanaPreview() = AccessoryDesignPreview(
    AccessoryCustomizationUiState(fur = CharacterFur.Sand, accessory = OnboardingAccessory.Bandana))

@Preview(name = "03 · Телефон · Закрытый предмет", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun AccessoryLockedPreview() = AccessoryDesignPreview(
    AccessoryCustomizationUiState(fur = CharacterFur.Russet, accessory = OnboardingAccessory.Hat))

@Preview(name = "04 · Компактный · Крупный текст", widthDp = 360, heightDp = 740,
    fontScale = 1.3f, showBackground = true)
@Composable
private fun AccessoryCompactPreview() = AccessoryDesignPreview(
    AccessoryCustomizationUiState(accessory = OnboardingAccessory.None))

@Preview(name = "05 · Планшет · Альбомный", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun AccessoryTabletLandscapePreview() = AccessoryDesignPreview(
    AccessoryCustomizationUiState(fur = CharacterFur.Sand, accessory = OnboardingAccessory.Bandana))

@Preview(name = "06 · Планшет · Портретный", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun AccessoryTabletPortraitPreview() = AccessoryDesignPreview()

@Composable
private fun AccessoryDesignPreview(initial: AccessoryCustomizationUiState = AccessoryCustomizationUiState()) {
    var state by rememberSaveable(stateSaver = listSaver<AccessoryCustomizationUiState, String>(
        save = { listOf(it.name, it.fur.name, it.accessory.name) },
        restore = { AccessoryCustomizationUiState(it[0], CharacterFur.valueOf(it[1]), OnboardingAccessory.valueOf(it[2])) },
    )) { mutableStateOf(initial) }
    MaterialTheme(colorScheme = lightColorScheme()) {
        AccessoryCustomizationScreen(state, customizationArtwork(), accessoryArtwork(customizationArtwork()),
            onSelect = { state = state.copy(accessory = it) },
            onBack = {}, onApply = {})
    }
}

@Preview(name = "07 · Телефон · Альбомный · Системные панели",
    device = "spec:width=891dp,height=411dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape",
    showSystemUi = true, showBackground = true)
@Composable
private fun AccessoryPhoneLandscapePreview() = AccessoryDesignPreview()
