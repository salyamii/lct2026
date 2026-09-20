package ru.nksk.lctapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.game.GameRepository

@UninstallModules(GameRepositoryModule::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationRestorationTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @BindValue @JvmField
    val onboardingDrafts: ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository =
        ru.nksk.lctapp.TestOnboardingDraftRepository()

    @BindValue
    @JvmField
    val repository: GameRepository = TestGameRepository()

    @BindValue @JvmField
    val content: StoryContentRepository = TestStoryContentRepository()

    @Test
    fun allFeatureRoutesRestoreFromSavedStateWithMenuUnderneath() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LCTAppTheme { LctNavHost() } }
        fun label(id: Int) = hasText(compose.activity.getString(id))
        val actions = listOf(
            label(R.string.menu_gear) to R.string.gear_title,
            label(R.string.menu_tasks) to R.string.menu_tasks,
            label(R.string.menu_goal) to R.string.menu_goal,
            hasContentDescription(compose.activity.getString(R.string.menu_coins_accessibility, 100)) to
                R.string.menu_coins,
            hasContentDescription(compose.activity.getString(R.string.menu_village)) to R.string.menu_village,
            label(R.string.menu_continue) to R.string.menu_continue,
        )

        actions.forEach { (action, title) ->
            compose.onNode(action and hasClickAction()).performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.navigation_back)).assertIsDisplayed()

            restoration.emulateSavedInstanceStateRestore()

            compose.onNodeWithText(when (title) { R.string.menu_continue -> "Смотритель просит помочь"; R.string.menu_goal -> "Большие цели"; else -> compose.activity.getString(title) }).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.navigation_back))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText("Выбрать большую цель")
                .assertIsDisplayed()
        }
    }
    @Test
    fun miniGameRoutesRestoreWithTheHubBelowThem() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LCTAppTheme { LctNavHost() } }
        val games = listOf(R.string.deeds_star_title, R.string.deeds_price_title, R.string.deeds_target_title)
        games.forEach { title ->
            compose.onNodeWithText(compose.activity.getString(R.string.menu_tasks)).performClick()
            compose.onNodeWithText(compose.activity.getString(title)).performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(1_000)
            restoration.emulateSavedInstanceStateRestore()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText(compose.activity.getString(title)).assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.navigation_back)).performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText(compose.activity.getString(R.string.menu_tasks)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.navigation_back)).performClick()
        }
    }

}
