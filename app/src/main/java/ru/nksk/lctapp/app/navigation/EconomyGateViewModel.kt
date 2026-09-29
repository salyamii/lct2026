package ru.nksk.lctapp.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.game.GameRepository

internal data class EconomyGateState(val loading: Boolean = true, val failed: Boolean = false,
    val planning: BudgetPlanning? = null)

/** App-level routing observes persisted planning; feature screens do not import each other. */
@HiltViewModel
internal class EconomyGateViewModel @Inject constructor(private val games: GameRepository) : ViewModel() {
    private val state = MutableStateFlow(EconomyGateState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    init { retry() }
    fun retry() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch {
            state.value = EconomyGateState()
            try {
                games.observe().collect { game ->
                    // A completed run briefly has no active world while the app leaves for
                    // character setup. Keep the outgoing entry alive to deliver that callback.
                    if (game != null) state.value = EconomyGateState(loading = false, planning = game.economy.planning)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = EconomyGateState(loading = false, failed = true) }
        }
    }
}
