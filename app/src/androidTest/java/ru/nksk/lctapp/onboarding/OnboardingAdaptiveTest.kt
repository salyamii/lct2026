package ru.nksk.lctapp.onboarding

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import ru.nksk.lctapp.app.*
import ru.nksk.lctapp.feature.onboarding.ui.*

/** Layout assertions, not golden screenshots: exercises the real screen at each window size. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class OnboardingAdaptiveTest(private val width: Int, private val height: Int, private val fontScale: Float) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun edgeToEdgeHost() { compose.runOnUiThread { compose.activity.enableEdgeToEdge() } }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}dp font={2}")
        fun windows(): List<Array<Any>> = buildList {
            for (w in listOf(400, 610, 900)) for (h in listOf(400, 500, 1000)) add(arrayOf(w, h, 1f))
            add(arrayOf(360, 640, 1.5f))
            add(arrayOf(900, 400, 1.5f))
            add(arrayOf(1280, 800, 1f))
        }
    }

    @Test fun profileReflowsAndEveryChoiceRemainsReachable() {
        var continues = 0
        render {
            var state by remember { mutableStateOf(CustomizationUiState(name = "Искорка")) }
            CustomizationScreen(state, customizationArtwork(),
                { state = state.copy(name = it) }, { state = state.copy(temperament = it) },
                { state = state.copy(fur = it) }, {}, { continues++ })
        }
        assertActionSafe("Продолжить")
        val background = compose.onNodeWithTag("onboarding_scene_background").fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        assertEquals(root.left, background.left, 1f)
        assertEquals(root.top, background.top, 1f)
        val field = compose.onNodeWithTag("customization_editor").fetchSemanticsNode().boundsInRoot
        if (width >= 720) assertTrue("Editor should be beside the scene", field.left >= background.right)
        else assertTrue("Editor should be below the scene", field.top >= background.bottom - 1f)
        CharacterTemperament.entries.forEach {
            compose.onNodeWithText(it.label, substring = false).performScrollTo().performClick().assertIsSelected()
        }
        CharacterFur.entries.forEach {
            compose.onNodeWithText(it.label, substring = false).performScrollTo().performClick().assertIsSelected()
        }
        compose.onNodeWithText("Продолжить").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, continues) }
    }

    @Test fun accessoriesKeepArtworkAndActionReachableWithInsetsAndLargeText() {
        var applied = 0
        render {
            var state by remember { mutableStateOf(AccessoryCustomizationUiState()) }
            val art = customizationArtwork()
            AccessoryCustomizationScreen(state, art, accessoryArtwork(art),
                { state = state.copy(accessory = it) }, {}, { applied++ })
        }
        assertActionSafe("Применить")
        // The carousel may need vertical scrolling on a short window, but never compresses away.
        compose.onNodeWithTag("accessory_pager").performScrollTo().assertIsDisplayed()
        val pager = compose.onNodeWithTag("accessory_pager").fetchSemanticsNode().boundsInRoot
        assertTrue("Carousel has usable height", pager.height > 0)
        compose.onNodeWithText("Применить").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, applied) }
    }

    @Test fun introductionExplanationsCanBeReadAndStartRemainsAccessible() {
        var starts = 0
        render {
            val art = customizationArtwork()
            AdventureIntroductionScreen(art, art.copper, introductionIcons(), {}, { starts++ })
        }
        assertActionSafe("Начать приключение")
        listOf("Нужно", "Хочу", "В копилку", "Запас").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        }
        compose.onNodeWithText("Поможет, если в дороге понадобится ремонт или лечение.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Начать приключение").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, starts) }
    }

    @Test fun budgetExplanationScrollsAndContinueRemainsAccessible() {
        var continues = 0
        render {
            BudgetIntroductionScreen(
                artwork = customizationArtwork(),
                categoryArtwork = { category, modifier -> OnboardingBudgetArtwork(category, modifier) },
                onBack = {}, onContinue = { continues++ },
            )
        }
        assertActionSafe("Распределить монеты")
        listOf("Нужно", "Хочу", "В копилку", "Запас").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().assertHasNoClickAction()
        }
        compose.onNodeWithText("Когда решишь отложить монеты, переложи их в копилку.")
            .performScrollTo().assertIsDisplayed()
        assertActionSafe("Распределить монеты")
        compose.onNodeWithText("Распределить монеты").performClick()
        compose.runOnIdle { assertEquals(1, continues) }
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                val density = LocalDensity.current
                fun px(dp: Int) = with(density) { dp.dp.roundToPx() }
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(24), 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(24)))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(px(24), 0, px(24), 0))
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true).build()
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                    Box(Modifier.fillMaxSize().testTag("test_window")) { MaterialTheme { content() } }
                }
            }
        }
    }

    private fun assertActionSafe(label: String) {
        val action = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        val pxPerDp = root.width / width
        assertTrue("Action overlaps left cutout", action.left >= root.left + 24 * pxPerDp - 1)
        assertTrue("Action overlaps right cutout", action.right <= root.right - 24 * pxPerDp + 1)
        assertTrue("Action overlaps navigation bar", action.bottom <= root.bottom - 24 * pxPerDp + 1)
        assertTrue("Action overlaps status bar", action.top >= root.top + 24 * pxPerDp - 1)
        assertTrue("Action touch target is under 48dp", action.height >= 48 * pxPerDp - 1)
        // Probe the rendered navigation-bar area; geometry alone misses a white background strip.
        val pixels = compose.onNodeWithTag("test_window").captureToImage().toPixelMap()
        val color = pixels[(pixels.width * .9f).toInt(), pixels.height - 2]
        assertEquals("Navigation area red", 251f / 255, color.red, .015f)
        assertEquals("Navigation area green", 250f / 255, color.green, .015f)
        assertEquals("Navigation area blue", 239f / 255, color.blue, .015f)
    }
}
