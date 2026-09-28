package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.PipeEndpoints
import ru.nksk.lctapp.domain.minigame.PipesState

data class PipesGameUiState(val game: PipesState)

sealed interface PipesGameAction {
    data class Press(val cell: Int) : PipesGameAction
    data object Release : PipesGameAction
    data object Restart : PipesGameAction
}

@HiltViewModel
class PipesGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PipesGameUiState(restore()))
    val uiState = mutableUiState.asStateFlow()

    init { publish(uiState.value.game) }

    fun onAction(action: PipesGameAction) {
        when (action) {
            is PipesGameAction.Press -> publish(uiState.value.game.press(action.cell))
            PipesGameAction.Release -> publish(uiState.value.game.release())
            PipesGameAction.Restart -> publish(PipesState.createRandom())
        }
    }

    private fun publish(game: PipesState) {
        // Bounded transient session data; the domain model carries no Android annotations.
        savedState["endpoint_colors"] = game.endpoints.map { it.color }.toIntArray()
        savedState["endpoint_firsts"] = game.endpoints.map { it.first }.toIntArray()
        savedState["endpoint_seconds"] = game.endpoints.map { it.second }.toIntArray()
        savedState["path_colors"] = game.paths.keys.toIntArray()
        savedState["path_cells"] = game.paths.values.map { it.toIntArray() }.toTypedArray()
        savedState["active_color"] = game.activeColor
        savedState["active_path"] = game.activePath.toIntArray()
        mutableUiState.value = PipesGameUiState(game)
    }

    private fun restore(): PipesState {
        val colors = savedState.get<IntArray>("endpoint_colors")
            ?: return PipesState.createRandom()
        val endpoints = colors.indices.map { i ->
            PipeEndpoints(
                color = colors[i],
                first = requireNotNull(savedState.get<IntArray>("endpoint_firsts"))[i],
                second = requireNotNull(savedState.get<IntArray>("endpoint_seconds"))[i],
            )
        }
        val pathColors = savedState.get<IntArray>("path_colors") ?: IntArray(0)
        // Accept the former in-memory List shape as well as the saved array representation.
        val pathCells = when (val saved = savedState.get<Any>("path_cells")) {
            null -> emptyList()
            is Array<*> -> saved.map { it as IntArray }
            is List<*> -> saved.map { it as IntArray }
            else -> error("Unsupported saved pipe path representation")
        }
        val paths = pathColors.indices.associate { i -> pathColors[i] to pathCells[i].toList() }
        return PipesState(
            endpoints = endpoints,
            paths = paths,
            activeColor = savedState["active_color"],
            activePath = savedState.get<IntArray>("active_path")?.toList().orEmpty(),
        )
    }
}
