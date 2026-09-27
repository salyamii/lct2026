package ru.nksk.lctapp

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import ru.nksk.lctapp.app.navigation.AppNavigationSavedStateConfiguration
import ru.nksk.lctapp.feature.economy.navigation.Economy as Coins
import ru.nksk.lctapp.feature.economy.navigation.Savings
import ru.nksk.lctapp.feature.learning.navigation.Learning
import ru.nksk.lctapp.feature.learning.navigation.SkillTraining
import ru.nksk.lctapp.feature.learning.navigation.OtherPaths
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.gear.navigation.Gear
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.tasks.navigation.StarPlates
import ru.nksk.lctapp.feature.tasks.navigation.PriceCheck
import ru.nksk.lctapp.feature.tasks.navigation.Telescope
import ru.nksk.lctapp.feature.tasks.navigation.Tasks
import ru.nksk.lctapp.feature.tasks.navigation.DeedGame
import ru.nksk.lctapp.feature.map.navigation.GameMap
import ru.nksk.lctapp.feature.settings.navigation.Settings

/** Compatibility with the Navigation 3 Android serializer used before the package cleanup. */
@RunWith(AndroidJUnit4::class)
class NavigationKeyCompatibilityTest {
    private val serializer = PolymorphicSerializer(NavKey::class)
    private val configuration = AppNavigationSavedStateConfiguration
    private val legacyRoutes = listOf(
        "ru.nksk.lctapp.ui.menu.MainMenu" to MainMenu,
        "ru.nksk.lctapp.ui.coins.navigation.Coins" to Coins,
        "ru.nksk.lctapp.ui.day.navigation.Day" to Day,
        "ru.nksk.lctapp.ui.gear.navigation.Gear" to Gear,
        "ru.nksk.lctapp.ui.goal.navigation.Goal" to Goal,
        "ru.nksk.lctapp.ui.tasks.navigation.Tasks" to Tasks,
        "ru.nksk.lctapp.ui.village.navigation.Village" to GameMap,
    )

    @Test
    fun legacyRouteFixturesDecodeToTheCurrentFeatureKeys() {
        for ((className, expected) in legacyRoutes) {
            // Explicit fixture: deriving this from the current encoder would hide a rename.
            val legacyState = savedState {
                putString("type", className)
                putSavedState("value", savedState())
            }

            assertEquals(
                className,
                expected,
                decodeFromSavedState(serializer, legacyState, configuration),
            )
        }
    }

    @Test
    fun encodedFeatureKeysUseStableIdsInsteadOfRuntimeClassNames() {
        val stableRoutes = listOf(
            "main_menu" to MainMenu,
            "settings" to Settings,
            "coins" to Coins,
            "savings" to Savings,
            "financial_learning" to Learning,
            "skill_training" to SkillTraining,
            "other_paths" to OtherPaths(9),
            "day" to Day,
            "gear" to Gear,
            "goal" to Goal,
            "tasks" to Tasks,
            "tasks_star_plates" to StarPlates,
            "tasks_price_check" to PriceCheck,
            "tasks_telescope" to Telescope,
            "deed_game" to DeedGame("day-1:proposal:offer:execution"),
            "village" to GameMap,
        )
        for ((id, route) in stableRoutes) {
            val encoded = encodeToSavedState(serializer, route, configuration)

            assertEquals(id, id, encoded.read { getString("type") })
            assertEquals(id, route, decodeFromSavedState(serializer, encoded, configuration))
        }
    }

    @Test
    fun legacyBackStacksRestoreMenuAndDestinationInOrder() {
        val stackSerializer = NavBackStackSerializer(serializer)
        for ((className, destination) in legacyRoutes.drop(1)) {
            val legacyStack = savedState {
                putSavedState("0", savedState {
                    putString("type", "ru.nksk.lctapp.ui.menu.MainMenu")
                    putSavedState("value", savedState())
                })
                putSavedState("1", savedState {
                    putString("type", className)
                    putSavedState("value", savedState())
                })
            }

            val restored = decodeFromSavedState(stackSerializer, legacyStack, configuration)

            assertEquals(className, listOf(MainMenu, destination), restored.toList())
            val reencoded = encodeToSavedState(stackSerializer, restored, configuration)
            assertEquals(
                className,
                restored.toList(),
                decodeFromSavedState(stackSerializer, reencoded, configuration).toList(),
            )
        }
    }

    @Test
    fun savedOtherPathsFixtureKeepsTheDayWhoseSummaryOpenedIt() {
        val savedRoute = savedState {
            putString("type", "other_paths")
            putSavedState("value", savedState { putInt("day", 9) })
        }

        assertEquals(OtherPaths(9), decodeFromSavedState(serializer, savedRoute, configuration))
    }

    @Test
    fun unknownRouteIdsAreRejected() {
        val unknownRoute = savedState {
            putString("type", "unregistered_route")
            putSavedState("value", savedState())
        }

        assertThrows(SerializationException::class.java) {
            decodeFromSavedState(serializer, unknownRoute, configuration)
        }
    }
}
