package ru.nksk.lctapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.app.di.GameRepositoryModule
import ru.nksk.lctapp.app.di.InitialGameStateModule
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetLook
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetVisualState
import ru.nksk.lctapp.feature.menu.ui.MainMenuScreen
import ru.nksk.lctapp.feature.menu.ui.toMainMenuUiState

@HiltAndroidTest
@UninstallModules(InitialGameStateModule::class, GameRepositoryModule::class)
@RunWith(AndroidJUnit4::class)
class MainMenuInitialStateTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val game: GameState = createInitialGameState().let { initial ->
        initial.copy(
            economy = initial.economy.copy(balance = 3_000_000_000L),
            pet = PetState(PetLook.BANDANA, PetVisualState.HUNGRY),
        )
    }

    @BindValue
    @JvmField
    val repository: GameRepository = TestGameRepository(null)

    @Test
    fun menuEntryDisplaysTheHiltProvidedDomainSnapshot() {
        compose.setContent {
            LCTAppTheme { LctNavHost() }
        }

        compose.onNodeWithText("3000000000", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_coins_accessibility, 3_000_000_000L),
        )).assertIsDisplayed()
        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_pet_hungry),
        )).assertIsDisplayed()
        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_pet_bandana),
        )).assertDoesNotExist()
    }

    @Test
    fun screenUpdatesFromSpecialAppearanceToTheLatestSelectedLook() {
        val initial = createInitialGameState()
        val state = mutableStateOf(initial.copy(
            pet = PetState(PetLook.BANDANA, PetVisualState.HAPPY),
        ).toMainMenuUiState())
        compose.setContent {
            LCTAppTheme { MainMenuScreen(state = state.value, onAction = {}) }
        }
        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_pet_happy),
        )).assertIsDisplayed()

        compose.runOnIdle {
            state.value = initial.copy(
                pet = PetState(PetLook.HAT, PetVisualState.NORMAL),
            ).toMainMenuUiState()
        }

        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_pet_hat),
        )).assertIsDisplayed()
        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_pet_happy),
        )).assertDoesNotExist()
    }

    @Test
    fun unmappedStateDisplaysItsLabelWithoutTheBackpackImage() {
        val game = createInitialGameState().copy(
            pet = PetState(PetLook.BACKPACK, PetVisualState.NEEDS_HELP),
        )
        compose.setContent {
            LCTAppTheme { MainMenuScreen(state = game.toMainMenuUiState(), onAction = {}) }
        }

        compose.onNodeWithText(compose.activity.getString(R.string.menu_pet_needs_help))
            .assertIsDisplayed()
        compose.onNode(hasContentDescription(
            compose.activity.getString(R.string.menu_fox_description),
        )).assertDoesNotExist()
    }
}
