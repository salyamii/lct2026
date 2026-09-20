package ru.nksk.lctapp.feature.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.geometry.Rect
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

@Composable
private fun MapPreview(initialSelection: String = "city", showLocked: Boolean = false) {
    var selected by rememberSaveable(initialSelection) { mutableStateOf(initialSelection) }
    LCTAppTheme {
        LocationMapScreen(
            locations = mapLocations.map { location ->
                location.copy(isUnlocked = !showLocked || location.id in setOf("city", "workshop", "pier"))
            },
            currentLocationId = selected,
            onLocationClick = { selected = it },
            onBack = {},
        )
    }
}

@Preview(name = "Карта · день", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LocationMapDayPreview() = MapPreview()

@Preview(name = "Карта · выбрана мастерская", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun LocationMapWorkshopPreview() = MapPreview(initialSelection = "workshop")

@Preview(name = "Карта · компактный экран", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Карта · крупный текст", widthDp = 390, heightDp = 844, fontScale = 1.5f, showBackground = true)
@Preview(name = "Карта · альбомный экран", widthDp = 844, heightDp = 390, showBackground = true)
@Composable
private fun LocationMapAdaptivePreview() = MapPreview()

// Illustrative availability only; not a chapter-to-location rule.
@Preview(name = "Карта · закрытые локации", widthDp = 390, heightDp = 844, showBackground = true)
@Preview(name = "Карта · закрытые · крупный текст", widthDp = 390, heightDp = 844, fontScale = 1.5f, showBackground = true)
@Composable
private fun LocationMapLockedPreview() = MapPreview(showLocked = true)
