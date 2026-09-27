package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository

@UninstallModules(GameRepositoryModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainMenuNavigationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun waitForSavedGame() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Выбрать цель накопления")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @BindValue @JvmField
    val onboardingDrafts: ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository =
        ru.nksk.lctapp.TestOnboardingDraftRepository()

    @BindValue
    @JvmField
    val repository: GameRepository = TestGameRepository()

    @BindValue @JvmField
    val content: StoryContentRepository = TestStoryContentRepository()

    @Test
    fun everyMenuActionOpensItsDestinationAndReturnsToTheMenu() {
        compose.onNodeWithText("Выбрать цель накопления").assertHasNoClickAction()
        fun label(id: Int) = hasText(compose.activity.getString(id))
        val actions = listOf(
            hasContentDescription(compose.activity.getString(R.string.menu_coins_accessibility, 100)) to
                R.string.menu_coins,
            label(R.string.menu_gear) to R.string.gear_title,
            label(R.string.menu_tasks) to R.string.menu_tasks,
            label(R.string.menu_goal) to R.string.menu_goal,
            hasContentDescription(compose.activity.getString(R.string.menu_village)) to R.string.menu_village,
            label(R.string.menu_continue) to R.string.menu_continue,
        )

        actions.forEach { (action, title) ->
            compose.onNode(action and hasClickAction()).assertIsDisplayed().performClick()
            compose.onNodeWithText(when (title) {
                R.string.menu_village -> "Карта приключений"
                R.string.menu_continue -> "Первый бюджет"
                R.string.menu_goal -> "Цели"
                R.string.menu_coins -> "Первый бюджет"
                else -> compose.activity.getString(title)
            }).assertIsDisplayed()
            compose.onNode(hasText(compose.activity.getString(R.string.navigation_back)) or
                hasContentDescription(compose.activity.getString(R.string.navigation_back)) or hasContentDescription("В главное меню"))
                .assertIsDisplayed().performClick()
            assertMenuIsDisplayed()
        }
    }

    @Test
    fun systemBackReturnsToMenu() {
        compose.onNodeWithText(compose.activity.getString(R.string.menu_tasks)).performClick()
        compose.onNode(hasText(compose.activity.getString(R.string.navigation_back)) or hasContentDescription(compose.activity.getString(R.string.navigation_back))).assertIsDisplayed()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertMenuIsDisplayed()
    }

    @Test
    fun destinationAndBackStackSurviveActivityRecreation() {
        compose.onNode(hasContentDescription(compose.activity.getString(R.string.menu_village))).performClick()
        compose.onNode(hasText(compose.activity.getString(R.string.navigation_back)) or hasContentDescription(compose.activity.getString(R.string.navigation_back))).assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Карта приключений").assertIsDisplayed()
        compose.onNode(hasText(compose.activity.getString(R.string.navigation_back)) or hasContentDescription(compose.activity.getString(R.string.navigation_back)))
            .assertIsDisplayed().performClick()
        assertMenuIsDisplayed()
    }

    private fun assertMenuIsDisplayed() {
        compose.onNodeWithText("Выбрать цель накопления").assertIsDisplayed()
        compose.onNode(
            hasContentDescription(compose.activity.getString(R.string.menu_coins_accessibility, 100)),
        ).assertIsDisplayed()
    }
}
