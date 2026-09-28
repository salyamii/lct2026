package ru.nksk.lctapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.feature.debug.ui.DebugOverlay

class DebugOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun resetRequiresExplicitConfirmation() {
        var resetCount = 0
        compose.setContent {
            DebugOverlay(onResetProgress = { resetCount++ }) { launcher -> launcher() }
        }

        compose.onNodeWithContentDescription("Открыть отладку").performClick()
        compose.onNodeWithText("Сбросить прогресс и перезапустить").performClick()
        compose.onNodeWithText("Сбросить прогресс?").assertExists()
        compose.runOnIdle { assertEquals(0, resetCount) }

        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithText("Сбросить прогресс?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, resetCount) }

        compose.onNodeWithText("Сбросить прогресс и перезапустить").performClick()
        compose.runOnIdle { assertEquals(0, resetCount) }
        compose.onNodeWithText("Сбросить и перезапустить").performClick()
        compose.onNodeWithText("Сбросить прогресс?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, resetCount) }
    }
}
