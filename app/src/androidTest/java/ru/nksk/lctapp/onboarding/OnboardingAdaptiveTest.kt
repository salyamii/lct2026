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
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.theme.Nunito
import ru.nksk.lctapp.core.ui.theme.Rubik
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
            add(arrayOf(320, 480, 2f))
            add(arrayOf(360, 740, 2f))
            add(arrayOf(640, 300, 1f))
            add(arrayOf(1280, 800, 1f))
        }
    }

    @Test fun welcomeActionDoesNotCollapseAfterTheCharacterSceneAndLongText() {
        var starts = 0
        render {
            OnboardingScreen(OnboardingUiState(foxSelected = true),
                OnboardingArtwork(R.drawable.onboarding_castle, R.drawable.onboarding_ryzhik,
                    R.drawable.npc_luna_body, R.drawable.onboarding_tiko_unavailable,
                    R.drawable.menu_ground_shadow, Rubik, Nunito),
                saving = false, saveFailed = true, onAction = {}, onStart = { starts++ })
        }
        assertActionSafe("Начать приключение", scrollAction = true, darkNavigation = true)
        compose.onNodeWithText("Начать приключение").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, starts) }
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
        val field = compose.onNodeWithTag("customization_editor").fetchSemanticsNode().boundsInRoot
        if (!scrollsWholeScreen) {
            assertEquals(root.left, background.left, 1f)
            assertEquals(root.top, background.top, 1f)
            if (width >= 720) assertTrue("Editor should be beside the scene", field.left >= background.right)
            else assertTrue("Editor should be below the scene", field.top >= background.bottom - 1f)
        }
        CharacterTemperament.entries.forEach {
            compose.onNodeWithText(it.label, substring = false).performScrollTo().performClick().assertIsSelected()
        }
        CharacterFur.entries.forEach {
            compose.onNodeWithText(it.label, substring = false).performScrollTo().performClick().assertIsSelected()
        }
        assertActionSafe("Продолжить")
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
        assertActionSafe("Применить")
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
        assertActionSafe("Начать приключение")
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

    @Test fun welcomeCardCoversTheBottomOfTallWindowsWithoutExposingTheScene() {
        // This checks unused space after a fully visible card. On short windows, scrolling
        // the button into view can put the button itself under the bottom pixel probe.
        // Reachability and minimum action height are covered by the separate welcome test.
        if (height < 1000 || fontScale != 1f || (width >= 700 && width > height)) return
        render {
            OnboardingScreen(OnboardingUiState(foxSelected = true),
                OnboardingArtwork(R.drawable.onboarding_castle, R.drawable.onboarding_ryzhik,
                    R.drawable.npc_luna_body, R.drawable.onboarding_tiko_unavailable,
                    R.drawable.menu_ground_shadow, Rubik, Nunito),
                saving = false, saveFailed = false, onAction = {}, onStart = {})
        }
        assertActionSafe("Начать приключение", scrollAction = false, darkNavigation = true)
        val root = compose.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("onboarding_welcome_card").fetchSemanticsNode().boundsInRoot
        val pxPerDp = root.width / width
        assertTrue("Castle is visible below the welcome card", card.bottom >= root.bottom - 24 * pxPerDp - 1)
        // Probe above the system bar too: its own painted strip used to hide the gap from this test.
        val pixels = compose.onNodeWithTag("test_window").captureToImage().toPixelMap()
        val color = pixels[pixels.width / 2, (pixels.height - 26 * pxPerDp).toInt()]
        assertTrue("Card background must cover the bottom of the content viewport",
            color.red < .2f && color.green < .15f && color.blue < .35f)
    }

    @Test fun goalBriefingKeepsItsActionAfterLongCopyAndSaveError() {
        var continues = 0
        render {
            AdventureGoalBriefingScreen(customizationArtwork(), {}, { continues++ }, saveFailed = true)
        }
        assertActionSafe("Дальше")
        compose.onNodeWithText("Дальше").performClick()
        compose.runOnIdle { assertEquals(1, continues) }
    }

    @Test fun goalSelectionKeepsTheChoiceAndConfirmActionReachable() {
        var confirms = 0
        render {
            val goals = onboardingGoalOptions()
            var selected by remember { mutableStateOf(goals.first().id) }
            AdventureGoalSelectionScreen(customizationArtwork(), goals, selected,
                { selected = it }, {}, { confirms++ }, saveFailed = true)
        }
        compose.onNodeWithText("Поездка").performScrollTo().performClick().assertIsSelected()
        assertActionSafe("Начать с этого")
        compose.onNodeWithText("Начать с этого").performClick()
        compose.runOnIdle { assertEquals(1, confirms) }
    }

    @Test fun profileActionCanBeReachedWhileKeyboardUsesTheBottomOfTheWindow() {
        render(imeHeight = 220) {
            CustomizationScreen(CustomizationUiState(name = "Искорка"), customizationArtwork(),
                {}, {}, {}, {}, {})
        }
        assertActionSafe("Продолжить", bottomInset = 220)
    }

    private val scrollsWholeScreen get() = height < 480 || fontScale >= 1.5f

    private fun render(imeHeight: Int = 0, content: @Composable () -> Unit) {
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                DeviceConfigurationOverride.FontScale(fontScale)) {
                val density = LocalDensity.current
                fun px(dp: Int) = with(density) { dp.dp.roundToPx() }
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(24), 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(24)))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(px(24), 0, px(24), 0))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px(imeHeight)))
                    .setVisible(WindowInsetsCompat.Type.ime(), imeHeight > 0)
                    .setVisible(WindowInsetsCompat.Type.systemBars(), true).build()
                DeviceConfigurationOverride(DeviceConfigurationOverride.WindowInsets(insets)) {
                    Box(Modifier.fillMaxSize().testTag("test_window")) { MaterialTheme { content() } }
                }
            }
        }
    }

    private fun assertActionSafe(label: String, scrollAction: Boolean = scrollsWholeScreen,
        darkNavigation: Boolean = false, bottomInset: Int = 24) {
        if (scrollAction) compose.onNodeWithText(label).performScrollTo()
        val action = compose.onNodeWithText(label).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInRoot
        val root = compose.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        val pxPerDp = root.width / width
        assertTrue("Action overlaps left cutout", action.left >= root.left + 24 * pxPerDp - 1)
        assertTrue("Action overlaps right cutout", action.right <= root.right - 24 * pxPerDp + 1)
        assertTrue("Action overlaps navigation bar or keyboard", action.bottom <= root.bottom - bottomInset * pxPerDp + 1)
        assertTrue("Action overlaps status bar", action.top >= root.top + 24 * pxPerDp - 1)
        assertTrue("Action is compressed below its 56dp minimum", action.height >= 56 * pxPerDp - 1)
        // Probe the rendered navigation-bar area; geometry alone misses a white background strip.
        val pixels = compose.onNodeWithTag("test_window").captureToImage().toPixelMap()
        val color = pixels[(pixels.width * .9f).toInt(), pixels.height - 2]
        assertEquals("Navigation area red", (if (darkNavigation) 31f else 251f) / 255, color.red, .015f)
        assertEquals("Navigation area green", (if (darkNavigation) 20f else 250f) / 255, color.green, .015f)
        assertEquals("Navigation area blue", (if (darkNavigation) 71f else 239f) / 255, color.blue, .015f)
    }
}
