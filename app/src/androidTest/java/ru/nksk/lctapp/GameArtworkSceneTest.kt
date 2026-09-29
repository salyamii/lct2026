package ru.nksk.lctapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.nksk.lctapp.core.ui.components.GameArtworkScene
import ru.nksk.lctapp.core.ui.components.ReportGameArtworkLoad
import ru.nksk.lctapp.core.ui.components.gameArtworkVisibility
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

/** Controlled completion reports keep these checks independent of Coil cache timing and I/O. */
class GameArtworkSceneTest {
    @get:Rule val compose = createComposeRule()

    @Test fun subcomposedForegroundWaitsForItsGroupWhileBackgroundAndControlsStayUsable() {
        var firstReady by mutableStateOf(false)
        var secondReady by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            LCTAppTheme {
                GameArtworkScene("scene", Modifier.size(200.dp).background(Color.Blue).testTag("scene")) {
                    // Production adventure/menu stages also register their requests in subcomposition.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        ReportGameArtworkLoad("first", firstReady)
                        ReportGameArtworkLoad("second", secondReady)
                        Box(Modifier.size(minOf(maxWidth, 64.dp)).then(gameArtworkVisibility()).background(Color.Red)) {
                            Box(Modifier.fillMaxSize().semantics { contentDescription = "Foreground object" })
                        }
                        Button({ clicks++ }, Modifier.align(Alignment.BottomCenter).testTag("action")) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
        assertCorner(Color.Blue)
        compose.onNodeWithContentDescription("Foreground object").assertDoesNotExist()
        val before = compose.onNodeWithTag("action").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("action").performClick()
        assertEquals(1, clicks)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()

        compose.runOnIdle { firstReady = true }
        assertCorner(Color.Blue)
        compose.runOnIdle { secondReady = true }
        assertCorner(Color.Red)
        compose.onNodeWithContentDescription("Foreground object").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("action").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun removalOfUnresolvedArtReleasesTheGroupAndLaterReplacementDoesNotHideIt() {
        var includeUnresolved by mutableStateOf(true)
        var request by mutableStateOf("initial")
        var ready by mutableStateOf(true)
        compose.setContent {
            GameArtworkScene("scene", Modifier.size(200.dp).background(Color.Blue).testTag("scene")) {
                ReportGameArtworkLoad(request, ready)
                if (includeUnresolved) ReportGameArtworkLoad("removed", settled = false)
                Box(Modifier.size(64.dp).then(gameArtworkVisibility()).background(Color.Red))
            }
        }
        assertCorner(Color.Blue)
        compose.runOnIdle { includeUnresolved = false }
        assertCorner(Color.Red)
        compose.runOnIdle { request = "replacement"; ready = false }
        assertCorner(Color.Red)
    }

    @Test fun aNewSceneWaitsAgainAndTerminalErrorCanReleaseIt() {
        var sceneKey by mutableStateOf("first")
        var settled by mutableStateOf(true)
        compose.setContent {
            GameArtworkScene(sceneKey, Modifier.size(200.dp).background(Color.Blue).testTag("scene")) {
                ReportGameArtworkLoad("same-artwork", settled)
                Box(Modifier.size(64.dp).then(gameArtworkVisibility()).background(Color.Red))
            }
        }
        assertCorner(Color.Red)
        compose.runOnIdle { sceneKey = "second"; settled = false }
        assertCorner(Color.Blue)
        // The contract reports success and terminal error as settled, avoiding a permanent barrier.
        compose.runOnIdle { settled = true }
        assertCorner(Color.Red)
    }

    private fun assertCorner(expected: Color) {
        val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        // Sample inside the foreground square, away from the action at the bottom.
        val actual = pixels[pixels.width / 8, pixels.height / 4]
        assertTrue("Expected $expected but found $actual", kotlin.math.abs(actual.red - expected.red) < .02f &&
            kotlin.math.abs(actual.green - expected.green) < .02f && kotlin.math.abs(actual.blue - expected.blue) < .02f)
    }
}
