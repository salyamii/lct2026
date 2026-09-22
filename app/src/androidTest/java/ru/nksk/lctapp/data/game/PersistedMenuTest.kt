package ru.nksk.lctapp.data.game

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.HiltTestActivity
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.pet.PetAge
import ru.nksk.lctapp.domain.pet.PetVisualState

@HiltAndroidTest
@UninstallModules(GameRepositoryModule::class)
@RunWith(AndroidJUnit4::class)
class PersistedMenuTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<HiltTestActivity>()
    private val db = Room.inMemoryDatabaseBuilder<GameDatabase>(ApplicationProvider.getApplicationContext<android.content.Context>())
        .setDriver(BundledSQLiteDriver()).build()
    @BindValue @JvmField
    val onboardingDrafts: ru.nksk.lctapp.domain.onboarding.OnboardingDraftRepository =
        ru.nksk.lctapp.TestOnboardingDraftRepository()

    @BindValue
    @JvmField
    val games: GameRepository = RoomGameRepository(db)

    @BindValue @JvmField
    val content: ru.nksk.lctapp.domain.content.StoryContentRepository = RoomStoryContentRepository(db)

    @After fun close() { db.close() }

    @Test fun menuRestoresSavedValuesAndObservesDatabaseUpdates() {
        val initial = createInitialGameState()
        runBlocking {
            games.initializeIfAbsent(initial.copy(
                economy = initial.economy.withTotalBalance(247),
                pet = initial.pet.copy(selectedLookId = "HAT", visualState = PetVisualState.UPSET, name = "Тоша"),
            ))
        }
        compose.setContent { LCTAppTheme { LctNavHost() } }
        val expandBudget = compose.activity.getString(R.string.menu_budget_expand)
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription(expandBudget).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(expandBudget).performClick()
        awaitAmount(247L)
        compose.onNode(hasContentDescription(compose.activity.getString(R.string.menu_pet_upset, "Тоша"))).assertIsDisplayed()
        compose.onNodeWithText("Тоша").assertIsDisplayed()
        compose.onNodeWithText("Ребёнок").assertDoesNotExist()
        runBlocking {
            games.update { it.copy(economy = it.economy.withTotalBalance(37),
                pet = it.pet.transitionTo(PetVisualState.NORMAL).copy(name = "Мика", age = PetAge.TEEN)) }
        }
        awaitAmount(37L)
        compose.onNode(hasContentDescription(compose.activity.getString(R.string.menu_pet_hat, "Мика"))).assertIsDisplayed()
        compose.onNodeWithText("Мика").assertIsDisplayed()
        compose.onNodeWithText("Подросток").assertDoesNotExist()
        compose.onNodeWithText("Тоша").assertDoesNotExist()
    }

    private fun awaitAmount(amount: Long) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(amount.toString(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(amount.toString(), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Запас", useUnmergedTree = true).assertIsDisplayed()
    }
}
