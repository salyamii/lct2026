package ru.nksk.lctapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.components.AdaptiveActionPanel
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

@OptIn(ExperimentalTestApi::class)
class AdaptiveActionPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortPanelAndLargeTextKeepEveryActionFullHeightAndReachable() {
        var lastActionClicks = 0
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 190.dp))
                then DeviceConfigurationOverride.FontScale(1.6f)) {
                LCTAppTheme {
                    AdaptiveActionPanel(Modifier.fillMaxSize(), actions = {
                        GameActionButton("Посмотреть другой результат", {}, Modifier.testTag("first"))
                        GameActionButton("Вернуться к сравнению", {}, Modifier.testTag("second"))
                        GameActionButton("Продолжить приключение", { lastActionClicks++ }, Modifier.testTag("last"))
                    }) {
                        Text("Короткий экран сохраняет объяснение и все доступные действия")
                    }
                }
            }
        }
        for (tag in listOf("first", "second", "last")) {
            compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(56.dp)
        }
        compose.onNodeWithTag("last").performClick()
        assertEquals(1, lastActionClicks)
    }

    @Test fun FittingActionsStayVisibleWhileLongBodyScrolls() {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 400.dp))) {
                LCTAppTheme {
                    AdaptiveActionPanel(Modifier.fillMaxSize(), actions = {
                        GameActionButton("Продолжить", {}, Modifier.testTag("action"))
                    }) {
                        repeat(24) { Text("Строка $it") }
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Строка 23").performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithTag("action").assertIsDisplayed().assertHeightIsAtLeast(56.dp)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
    }
}
