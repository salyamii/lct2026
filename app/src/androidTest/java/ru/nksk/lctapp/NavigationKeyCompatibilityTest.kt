package ru.nksk.lctapp

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.feature.coins.navigation.Coins
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.gear.navigation.Gear
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.tasks.navigation.Tasks
import ru.nksk.lctapp.feature.village.navigation.Village

/** Compatibility with the Navigation 3 Android serializer used before the package cleanup. */
@RunWith(AndroidJUnit4::class)
class NavigationKeyCompatibilityTest {
    private val serializer = NavKeySerializer<NavKey>()
    private val legacyRoutes = listOf(
        "ru.nksk.lctapp.ui.menu.MainMenu" to MainMenu,
        "ru.nksk.lctapp.ui.coins.navigation.Coins" to Coins,
        "ru.nksk.lctapp.ui.day.navigation.Day" to Day,
        "ru.nksk.lctapp.ui.gear.navigation.Gear" to Gear,
        "ru.nksk.lctapp.ui.goal.navigation.Goal" to Goal,
        "ru.nksk.lctapp.ui.tasks.navigation.Tasks" to Tasks,
        "ru.nksk.lctapp.ui.village.navigation.Village" to Village,
    )

    @Test
    fun legacyRouteFixturesDecodeToTheCurrentFeatureKeys() {
        for ((className, expected) in legacyRoutes) {
            // Explicit fixture: deriving this from the current encoder would hide a rename.
            val legacyState = savedState {
                putString("type", className)
                putSavedState("value", savedState())
            }

            assertEquals(className, expected, decodeFromSavedState(serializer, legacyState))
        }
    }

    @Test
    fun encodedFeatureKeysKeepTheirLegacyRuntimeClassNames() {
        for ((className, route) in legacyRoutes) {
            val encoded = encodeToSavedState(serializer, route)

            assertEquals(className, className, encoded.read { getString("type") })
        }
    }
}
