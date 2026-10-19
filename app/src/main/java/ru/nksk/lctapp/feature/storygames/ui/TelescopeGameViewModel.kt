package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopeAssembly
import ru.nksk.lctapp.domain.minigame.chapterone.TelescopePart

data class TelescopeGameUiState(val game: TelescopeAssembly = TelescopeAssembly())

sealed interface TelescopeGameAction {
    data class Select(val part: TelescopePart) : TelescopeGameAction
    data class Install(val part: TelescopePart) : TelescopeGameAction
    data class Focus(val value: Float) : TelescopeGameAction
    data object Observe : TelescopeGameAction
    data object Restart : TelescopeGameAction
}

@HiltViewModel
class TelescopeGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TelescopeGameUiState(restoreOrCreate()))
    val uiState = mutableUiState.asStateFlow()

    init {
        save(mutableUiState.value.game)
    }

    fun onAction(action: TelescopeGameAction) {
        val game = mutableUiState.value.game
        val next = when (action) {
            is TelescopeGameAction.Select -> game.select(action.part)
            is TelescopeGameAction.Install -> game.install(action.part)
            is TelescopeGameAction.Focus -> game.adjustFocus(action.value)
            TelescopeGameAction.Observe -> game.observe()
            TelescopeGameAction.Restart -> TelescopeAssembly.create(previous = game)
        }
        if (next == game) return
        save(next)
        mutableUiState.value = TelescopeGameUiState(next)
    }

    private fun restoreOrCreate(): TelescopeAssembly {
        val hasProgress = listOf(
            "telescope_save_version", "telescope_installed", "telescope_selected",
            "telescope_focus", "telescope_complete", "telescope_sky_variant", "telescope_focus_target",
        ).any(savedState::contains)
        if (!hasProgress) return TelescopeAssembly.create()
        return TelescopeAssembly.restore(
            savedState["telescope_installed"] ?: 0, savedState["telescope_selected"],
            savedState["telescope_focus"] ?: .18f, savedState["telescope_complete"] ?: false,
            version = savedState["telescope_save_version"] ?: 1,
            skyVariant = savedState["telescope_sky_variant"] ?: 0,
            focusTarget = savedState["telescope_focus_target"] ?: TelescopeAssembly.FOCUS_TARGET,
        )
    }

    private fun save(next: TelescopeAssembly) {
        savedState["telescope_installed"] = next.installedCount
        savedState["telescope_selected"] = next.selectedPart?.ordinal
        savedState["telescope_focus"] = next.focus
        savedState["telescope_complete"] = next.completed
        savedState["telescope_save_version"] = TelescopeAssembly.SAVE_VERSION
        savedState["telescope_sky_variant"] = next.skyVariant
        savedState["telescope_focus_target"] = next.focusTarget
    }
}
