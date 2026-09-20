package ru.nksk.lctapp.feature.onboarding.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.app.customizationArtwork

/** Open this file in Android Studio's Split/Design mode; use Interactive mode to edit the design. */
@Preview(name = "01 · Медный / любопытный", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun CustomizationCopperPreview() = CustomizationDesignPreview()

@Preview(name = "02 · Песочный / уверенный", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun CustomizationSandPreview() = CustomizationDesignPreview(
    CustomizationUiState(temperament = CharacterTemperament.Confident, fur = CharacterFur.Sand),
)

@Preview(name = "03 · Тёмно-рыжий / радостный", widthDp = 402, heightDp = 874, showBackground = true)
@Composable
private fun CustomizationRussetPreview() = CustomizationDesignPreview(
    CustomizationUiState(temperament = CharacterTemperament.Joyful, fur = CharacterFur.Russet),
)

@Preview(name = "04 · Компактный / крупный текст", widthDp = 360, heightDp = 740, fontScale = 1.3f, showBackground = true)
@Composable
private fun CustomizationCompactPreview() = CustomizationDesignPreview()

@Preview(name = "05 · Планшет · Альбомный", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun CustomizationTabletLandscapePreview() = CustomizationDesignPreview(
    CustomizationUiState(temperament = CharacterTemperament.Confident, fur = CharacterFur.Sand),
)

@Preview(name = "06 · Планшет · Портретный", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun CustomizationTabletPortraitPreview() = CustomizationDesignPreview()

@Composable
private fun CustomizationDesignPreview(initial: CustomizationUiState = CustomizationUiState()) {
    var state by rememberSaveable(stateSaver = listSaver<CustomizationUiState, String>(
        save = { listOf(it.name, it.temperament.name, it.fur.name) },
        restore = { CustomizationUiState(it[0], CharacterTemperament.valueOf(it[1]), CharacterFur.valueOf(it[2])) },
    )) { mutableStateOf(initial) }
    MaterialTheme(colorScheme = lightColorScheme()) {
        CustomizationScreen(state, artwork = customizationArtwork(),
            onNameChange = { state = state.copy(name = it) },
            onTemperamentChange = { state = state.copy(temperament = it) },
            onFurChange = { state = state.copy(fur = it) },
            onBack = {}, onContinue = {})
    }
}

@Preview(name = "07 · Телефон · Альбомный · Системные панели",
    device = "spec:width=891dp,height=411dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape",
    showSystemUi = true, showBackground = true)
@Composable
private fun CustomizationPhoneLandscapePreview() = CustomizationDesignPreview()
