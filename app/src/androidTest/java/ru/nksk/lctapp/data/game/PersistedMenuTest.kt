package ru.nksk.lctapp.data.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.R
import ru.nksk.lctapp.app.createInitialGameState
import ru.nksk.lctapp.app.navigation.LctNavHost
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.domain.pet.PetLook
import ru.nksk.lctapp.domain.pet.PetVisualState

@RunWith(AndroidJUnit4::class)
class PersistedMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val db = Room.inMemoryDatabaseBuilder<GameDatabase>(ApplicationProvider.getApplicationContext<android.content.Context>())
        .setDriver(BundledSQLiteDriver()).build()
    private val games = RoomGameRepository(db)

    @After fun close() { db.close() }

    @Test fun menuRestoresSavedValuesAndObservesDatabaseUpdates() {
        val initial = createInitialGameState()
        runBlocking {
            games.initializeIfAbsent(initial.copy(
                economy = initial.economy.copy(balance = 247),
                pet = initial.pet.copy(selectedLook = PetLook.HAT, visualState = PetVisualState.UPSET),
            ))
        }
        compose.setContent { LCTAppTheme { LctNavHost(gameRepository = games) } }
        awaitDescription(R.string.menu_coins_accessibility, 247L)
        compose.onNode(hasContentDescription(compose.activity.getString(R.string.menu_pet_upset))).assertIsDisplayed()
        runBlocking {
            games.update { it.copy(economy = it.economy.copy(balance = 37), pet = it.pet.transitionTo(PetVisualState.NORMAL)) }
        }
        awaitDescription(R.string.menu_coins_accessibility, 37L)
        compose.onNode(hasContentDescription(compose.activity.getString(R.string.menu_pet_hat))).assertIsDisplayed()
    }

    private fun awaitDescription(resource: Int, amount: Long) {
        val description = compose.activity.getString(resource, amount)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription(description)).assertIsDisplayed()
    }
}
