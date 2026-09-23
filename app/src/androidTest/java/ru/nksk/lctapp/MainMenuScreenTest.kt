package ru.nksk.lctapp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.feature.menu.ui.MainMenuPreviewState
import ru.nksk.lctapp.feature.menu.ui.MainMenuScreen
import ru.nksk.lctapp.feature.menu.ui.MainMenuAction
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainMenuScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun suppliedStateUpdatesVisibleProgressAndAccessibleCoinBalance() {
        val state = mutableStateOf(MainMenuPreviewState.copy(coins = 275L, completedGoals = 2, totalGoals = 7))
        compose.setContent {
            LCTAppTheme {
                MainMenuScreen(state = state.value, onAction = {})
            }
        }

        fun assertValues(coins: Int, completedGoals: Int, totalGoals: Int) {
            compose.onNodeWithText(coins.toString(), useUnmergedTree = true).assertIsDisplayed()
            compose.onNode(
                hasContentDescription(compose.activity.getString(R.string.menu_coins_accessibility, coins)),
            ).assertIsDisplayed()
            compose.onNodeWithText(
                compose.activity.getString(R.string.menu_goal_progress, completedGoals, totalGoals),
            ).assertIsDisplayed()
        }

        assertValues(coins = 275, completedGoals = 2, totalGoals = 7)
        compose.runOnIdle {
            state.value = MainMenuPreviewState.copy(coins = 40L, completedGoals = 3, totalGoals = 8)
        }
        assertValues(coins = 40, completedGoals = 3, totalGoals = 8)
    }

    @Test
    fun everyMenuButtonHasAnAccessibleClickAction() {
        verifyActions(DpSize(390.dp, 844.dp))
    }

    @Test
    fun compactPhoneWithLargeTextKeepsEveryActionReachable() {
        verifyActions(DpSize(360.dp, 640.dp), fontScale = 1.5f)
    }

    @Test
    fun landscapeWithLargeTextKeepsEveryActionReachableByScrolling() {
        verifyActions(DpSize(844.dp, 390.dp), fontScale = 1.5f, scrollToActions = true)
    }

    @Test
    fun villageAcceptsTouchesOnlyInsideItsFloatingButton() {
        val selections = mutableListOf<MainMenuAction>()
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1f)) {
                LCTAppTheme { MainMenuScreen(state = MainMenuPreviewState, onAction = selections::add) }
            }
        }
        val village = compose.onNode(
            hasContentDescription(compose.activity.getString(R.string.menu_village)) and hasClickAction(),
        )
        village.assertIsDisplayed()
        val bounds = village.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot()
        val origin = root.fetchSemanticsNode().boundsInRoot.topLeft
        assertEquals(root.fetchSemanticsNode().boundsInRoot.right, bounds.right, 1f)
        val edgeInset = with(compose.density) { 1.dp.toPx() }
        val outsideGap = with(compose.density) { 4.dp.toPx() }

        // The surrounding scene must not open the village.
        root.performTouchInput {
            click(Offset(bounds.center.x, bounds.top - outsideGap) - origin)
            click(Offset(bounds.center.x, bounds.bottom + outsideGap) - origin)
        }
        compose.runOnIdle { assertEquals(emptyList<MainMenuAction>(), selections) }

        root.performTouchInput {
            click(Offset(bounds.center.x, bounds.top + edgeInset) - origin)
            click(Offset(bounds.center.x, bounds.bottom - edgeInset) - origin)
        }
        compose.runOnIdle {
            assertEquals(listOf(MainMenuAction.Village, MainMenuAction.Village), selections)
        }
    }

    @Test
    fun idlePetHasNoClickActionAndDoesNotMoveTheMap() {
        val selections = mutableListOf<MainMenuAction>()
        compose.setContent {
            LCTAppTheme { MainMenuScreen(MainMenuPreviewState, selections::add) }
        }
        compose.mainClock.autoAdvance = false
        val pet = compose.onNodeWithTag("menu_pet")
        val map = compose.onNode(hasContentDescription(compose.activity.getString(R.string.menu_village)))
        val mapBefore = map.fetchSemanticsNode().boundsInRoot
        pet.assertHasNoClickAction()
        pet.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(4_200)
        assertEquals(mapBefore, map.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(emptyList<MainMenuAction>(), selections) }
    }

    private fun verifyActions(
        size: DpSize,
        fontScale: Float = 1f,
        scrollToActions: Boolean = false,
    ) {
        val selections = mutableListOf<MainMenuAction>()
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size) then
                    DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                LCTAppTheme {
                    MainMenuScreen(state = MainMenuPreviewState, onAction = selections::add)
                }
            }
        }

        fun label(id: Int) = hasText(compose.activity.getString(id))
        val name = compose.onNodeWithText(MainMenuPreviewState.pet.name)
        name.assertIsDisplayed().assertHasNoClickAction()
        name.performTouchInput { click() }
        compose.runOnIdle { assertEquals(emptyList<MainMenuAction>(), selections) }
        val actions = listOf(
            hasText(MainMenuPreviewState.goalTitle) to MainMenuAction.Goal,
            hasContentDescription(compose.activity.getString(R.string.menu_coins_accessibility, 100)) to
                MainMenuAction.Coins,
            hasContentDescription(compose.activity.getString(R.string.menu_village)) to MainMenuAction.Village,
            label(R.string.menu_gear) to MainMenuAction.Gear,
            label(R.string.menu_tasks) to MainMenuAction.Tasks,
            label(R.string.menu_goal) to MainMenuAction.Goal,
            label(R.string.menu_continue) to MainMenuAction.ContinueDay,
        )
        actions.forEach { (label, destination) ->
            val action = compose.onNode(label and hasClickAction())
            if (scrollToActions && destination != MainMenuAction.Village) action.performScrollTo()
            action.assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            compose.runOnIdle { assertEquals(destination, selections.lastOrNull()) }
        }
        compose.runOnIdle { assertEquals(actions.map { it.second }, selections) }
    }
}
