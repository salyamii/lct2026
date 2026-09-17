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
    private val serializer = PolymorphicSerializer(NavKey::class)
    private val configuration = AppNavigationSavedStateConfiguration
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
            "coins" to Coins,
            "day" to Day,
            "gear" to Gear,
            "goal" to Goal,
            "tasks" to Tasks,
            "village" to Village,
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
