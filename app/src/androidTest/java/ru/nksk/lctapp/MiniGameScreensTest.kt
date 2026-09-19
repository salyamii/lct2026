package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.minigame.MemoryState
import ru.nksk.lctapp.domain.minigame.PriceQuizState
import ru.nksk.lctapp.domain.minigame.QuizQuestion
import ru.nksk.lctapp.domain.minigame.TargetStopState
import ru.nksk.lctapp.feature.tasks.ui.*

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MiniGameScreensTest {
    private val ready = MiniGameSessionUiState(loading = false, available = true)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun quizMarksTheMoreExpensiveCardAfterAnAnswer() {
        compose.setContent {
            LCTAppTheme {
                PriceQuizScreen(
                    PriceQuizUiState(PriceQuizState(listOf(QuizQuestion(80, 20))).answer(true), session = ready),
                    onAction = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("80 монет").assertIsSelected()
        compose.onNodeWithText("20 монет").assertIsNotSelected()
    }

    @Test fun compactLandscapeKeepsTheLastMemoryCardReachable() {
        val actions = mutableListOf<MemoryGameAction>()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 320.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                LCTAppTheme {
                    MemoryGameScreen(
                        MemoryGameUiState(MemoryState(faces = (0..7).toList() + (0..7).toList()), session = ready),
                        onAction = actions::add, onBack = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Карта 16, закрыта")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(MemoryGameAction.Tap(15)), actions) }
    }

    @Test fun compactLandscapeKeepsTelescopeStopReachable() {
        val actions = mutableListOf<TargetStopAction>()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 320.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                LCTAppTheme {
                    TargetStopScreen(TargetStopUiState(TargetStopState(zoneStart = 40), session = ready), actions::add, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Стоп!").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("telescope_track").assertIsDisplayed()
        compose.onNodeWithText("Стоп!").performClick()
        compose.runOnIdle { assertEquals(1, actions.size) }
    }
}
