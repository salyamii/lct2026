package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun quizMarksTheMoreExpensiveCardAfterAnAnswer() {
        val actions = mutableListOf<PriceQuizAction>()
        compose.setContent {
            LCTAppTheme {
                PriceQuizScreen(
                    PriceQuizUiState(PriceQuizState(listOf(QuizQuestion(80, 20))).answer(true)),
                    onAction = actions::add, onBack = {},
                )
            }
        }
        compose.onNodeWithText("80 монет").assertIsSelected()
        compose.onNodeWithText("20 монет").assertIsNotSelected()
        compose.onNodeWithText("Верно! Счета сходятся 🎉").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Завершить").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(PriceQuizAction.Next(0)), actions) }
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
                        MemoryGameUiState(MemoryState(faces = (0..7).toList() + (0..7).toList())),
                        onAction = actions::add, onBack = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Карта 16, закрыта")
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(MemoryGameAction.Tap(15)), actions) }
    }

    @Test fun openAndMatchedMemoryCardsAnnounceTheirItemNames() {
        compose.setContent {
            LCTAppTheme {
                MemoryGameScreen(
                    MemoryGameUiState(MemoryState(faces = (0..7).toList() + (0..7).toList(),
                        faceUp = setOf(0), matched = setOf(1, 9))),
                    onAction = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Карта 1, ключ").assertExists()
        compose.onNodeWithContentDescription("Карта 2, армиллярная сфера, пара найдена").assertExists()
        compose.onNodeWithContentDescription("Карта 3, закрыта").assertExists()
    }

    @Test fun themedMemoryCardsAnnounceTheirActualArtworkInsteadOfTheDefaultSet() {
        compose.setContent {
            LCTAppTheme {
                MemoryGameScreen(
                    MemoryGameUiState(MemoryState(faces = (0..7).toList() + (0..7).toList(),
                        faceUp = setOf(0), matched = setOf(1, 9))),
                    onAction = {}, onBack = {},
                    deed = DeedGamePresentation("Архив", 0, true, storyAction = true,
                        pairArtwork = listOf(R.drawable.story_cargo_journal, R.drawable.story_observation_journal,
                            R.drawable.story_letter, R.drawable.story_old_photograph, R.drawable.story_instruction_journal,
                            R.drawable.story_route_map, R.drawable.story_map_missing_region, R.drawable.story_assembled_map)),
                )
            }
        }
        compose.onNodeWithContentDescription("Карта 1, журнал грузов").assertExists()
        compose.onNodeWithContentDescription("Карта 2, журнал наблюдений, пара найдена").assertExists()
        compose.onNodeWithContentDescription("Карта 3, закрыта").assertExists()
    }

    @Test fun compactLandscapeKeepsTelescopeStopReachable() {
        val actions = mutableListOf<TargetStopAction>()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(640.dp, 320.dp)) then
                    DeviceConfigurationOverride.FontScale(2f),
            ) {
                LCTAppTheme {
                    TargetStopScreen(TargetStopUiState(TargetStopState(zoneStart = 40)), actions::add, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Стоп!").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("telescope_track").assertIsDisplayed()
        compose.onNodeWithText("Стоп!").performClick()
        compose.runOnIdle { assertEquals(1, actions.size) }
    }

    @Test fun telescopeZoneAgreesWithTheStoppedMarkerAtTheAuditedPositions() {
        val shown = mutableStateOf(TargetStopUiState(
            TargetStopState(zoneStart = 5, round = 1, lastHit = false), stoppedPosition = .04f))
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 720.dp))) {
                LCTAppTheme { TargetStopScreen(shown.value, {}, {}) }
            }
        }
        fun centerIsInsideZone(): Boolean {
            val marker = compose.onNodeWithTag("telescope_marker").fetchSemanticsNode().boundsInRoot
            val zone = compose.onNodeWithTag("telescope_zone").fetchSemanticsNode().boundsInRoot
            return marker.center.x >= zone.left && marker.center.x < zone.right
        }
        assertFalse("A scored miss must visibly miss the zone", centerIsInsideZone())
        compose.runOnIdle {
            shown.value = TargetStopUiState(
                TargetStopState(zoneStart = 5, round = 1, hits = 1, lastHit = true), stoppedPosition = .24f)
        }
        assertTrue("A scored hit must visibly hit the zone", centerIsInsideZone())
    }
}
