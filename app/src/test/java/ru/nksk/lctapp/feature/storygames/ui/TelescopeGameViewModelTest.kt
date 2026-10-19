package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeAssembly
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopePart

class TelescopeGameViewModelTest {
    @Test fun freshGameIsSavedBeforeAnyTouchAndSurvivesRecreation() {
        val savedState = SavedStateHandle(mapOf("route" to "telescope"))
        val model = TelescopeGameViewModel(savedState)
        val initial = model.uiState.value.game
        assertEquals(4, savedState.get<Int>("telescope_save_version"))
        assertEquals(initial.skyVariant, savedState.get<Int>("telescope_sky_variant"))
        assertEquals(initial.focusTarget, savedState.get<Float>("telescope_focus_target"))
        assertTrue(abs(initial.focus - initial.focusTarget) > TelescopeAssembly.FOCUS_TOLERANCE)
        assertEquals(initial, TelescopeGameViewModel(savedState.recreated()).uiState.value.game)
    }

    @Test fun assemblyFocusAndFinishedResultRestoreTheSameGame() {
        val savedState = SavedStateHandle()
        var model = TelescopeGameViewModel(savedState)
        val initial = model.uiState.value.game
        TelescopePart.entries.forEach { part ->
            model.onAction(TelescopeGameAction.Select(part))
            model.onAction(TelescopeGameAction.Install(part))
        }
        model.onAction(TelescopeGameAction.Focus(initial.focusTarget))
        val focused = model.uiState.value.game
        assertTrue(focused.inFocus)
        assertFalse(focused.completed)
        val restoredState = savedState.recreated()
        model = TelescopeGameViewModel(restoredState)
        assertEquals(focused, model.uiState.value.game)
        model.onAction(TelescopeGameAction.Observe)
        assertTrue(model.uiState.value.game.completed)
        assertEquals(initial.skyVariant, model.uiState.value.game.skyVariant)
        assertEquals(initial.focusTarget, model.uiState.value.game.focusTarget, 0f)
        assertEquals(model.uiState.value.game, TelescopeGameViewModel(restoredState.recreated()).uiState.value.game)
    }

    @Test fun restartReplacesBothConditionsAndSavesThemImmediately() {
        val savedState = SavedStateHandle()
        val model = TelescopeGameViewModel(savedState)
        repeat(20) {
            val previous = model.uiState.value.game
            model.onAction(TelescopeGameAction.Restart)
            val next = model.uiState.value.game
            assertNotEquals(previous.skyVariant, next.skyVariant)
            assertTrue(abs(previous.focusTarget - next.focusTarget) > TelescopeAssembly.FOCUS_TOLERANCE * 2)
            assertEquals(next, TelescopeGameViewModel(savedState.recreated()).uiState.value.game)
        }
    }

    @Test fun legacyFinishedSaveKeepsItsSkyAndFocusWhenUpgraded() {
        for (version in listOf(null, 1, 2, 3)) {
            val savedState = SavedStateHandle(mapOf(
                "telescope_installed" to 3,
                "telescope_focus" to .68f,
                "telescope_complete" to true,
            ))
            if (version != null) savedState["telescope_save_version"] = version
            val model = TelescopeGameViewModel(savedState)
            val restored = model.uiState.value.game
            assertEquals(0, restored.skyVariant)
            assertEquals(TelescopeAssembly.FOCUS_TARGET, restored.focusTarget, 0f)
            assertTrue(restored.completed)
            assertEquals(4, savedState.get<Int>("telescope_save_version"))
            assertEquals(restored, TelescopeGameViewModel(savedState.recreated()).uiState.value.game)
        }
    }

    @Test fun invalidSavedConditionsAreNormalizedWithoutCompletingTheGame() {
        val savedState = SavedStateHandle(mapOf(
            "telescope_save_version" to 4,
            "telescope_installed" to 3,
            "telescope_focus" to .68f,
            "telescope_complete" to true,
            "telescope_sky_variant" to -4,
            "telescope_focus_target" to Float.NaN,
        ))
        val model = TelescopeGameViewModel(savedState)
        val restored = model.uiState.value.game
        assertFalse(restored.completed)
        assertEquals(0, restored.skyVariant)
        assertEquals(TelescopeAssembly.FOCUS_TARGET, restored.focusTarget, 0f)
        assertEquals(restored, TelescopeGameViewModel(savedState.recreated()).uiState.value.game)
    }

    private fun SavedStateHandle.recreated() = SavedStateHandle(keys().associateWith { get<Any?>(it) })
}
