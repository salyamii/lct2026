package ru.nksk.lctapp.feature.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.location.GameLocation
import ru.nksk.lctapp.domain.location.GameLocationController

data class MapUiState(
    val locations: List<MapLocationUi> = emptyList(),
    val selectedId: String = "",
    val loading: Boolean = true,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MapViewModel @Inject constructor(private val controller: GameLocationController) : ViewModel() {
    private val mutableState = MutableStateFlow(MapUiState())
    val uiState = mutableState.asStateFlow()
    private var observation: Job? = null

    init { reload() }

    fun reload() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = viewModelScope.launch {
            try {
                controller.observe().collect { map ->
                    mutableState.update { state ->
                        if (map == null) state.copy(loading = false, locations = emptyList(), error = "Сохранение игры недоступно")
                        else state.copy(
                            loading = false,
                            locations = mapLocations.map { it.copy(isUnlocked = GameLocation.fromCode(it.id) in map.availableLocations) },
                            selectedId = map.scene.location.code,
                        )
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutableState.update { it.copy(loading = false, locations = emptyList(), error = "Не удалось загрузить карту") }
            }
        }
    }

    fun select(id: String) {
        val state = uiState.value
        if (state.loading || state.saving || state.finished || state.locations.none { it.id == id && it.isUnlocked }) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                controller.selectLocation(GameLocation.fromCode(id))
                mutableState.update { it.copy(finished = true) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutableState.update { it.copy(saving = false, error = "Не удалось выбрать локацию. Попробуй ещё раз.") }
            }
        }
    }

    fun dismissError() { mutableState.update { it.copy(error = null) } }
}
