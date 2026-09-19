package ru.nksk.lctapp.feature.tasks.ui

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.MiniGameKind
import ru.nksk.lctapp.domain.minigame.MiniGameUnavailableException

data class MiniGameSessionUiState(
    val loading: Boolean = true,
    val available: Boolean = false,
    val saving: Boolean = false,
    val error: Boolean = false,
    val resultReady: Boolean = false,
    val successful: Boolean = false,
) {
    val canPlay: Boolean get() = !loading && available && !saving && !error && !resultReady
    val canRestart: Boolean get() = !loading && available && !saving && !error
}

/** Coordinates a transient board with one durable, idempotent aggregate result. */
internal class MiniGameSession(
    private val kind: MiniGameKind,
    private val repository: GameRepository,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onState: (MiniGameSessionUiState) -> Unit,
) {
    var state = MiniGameSessionUiState()
        private set
    private var latest: GameState? = null
    private var observer: Job? = null
    private var writer: Job? = null
    private var finished = false
    private var successful = false
    private var attemptId: String = savedState.get<String>("attempt_id")
        ?: UUID.randomUUID().toString().also { savedState["attempt_id"] = it }

    fun observe() {
        observer?.cancel()
        update(state.copy(loading = true, error = false))
        observer = scope.launch {
            try {
                repository.observe().collect { game ->
                    latest = checkNotNull(game) { "Game has not been initialized" }
                    update(state.copy(loading = false, available = kind.canPlay(game), error = false))
                    if (finished) saveResult()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                update(state.copy(loading = false, available = false, error = true))
            }
        }
    }

    fun finish(success: Boolean) {
        finished = true
        successful = success
        saveResult()
    }

    private fun saveResult() {
        if (latest == null || state.resultReady || writer?.isActive == true) return
        if (!successful) {
            update(state.copy(resultReady = true, successful = false))
            return
        }
        update(state.copy(saving = true, error = false, successful = true))
        writer = scope.launch {
            try {
                val saved = repository.update { current -> kind.complete(current, attemptId) }
                latest = saved
                update(state.copy(saving = false, error = false, resultReady = true, available = kind.canPlay(saved)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: MiniGameUnavailableException) {
                update(state.copy(saving = false, resultReady = true, successful = false, available = false))
            } catch (_: Exception) {
                update(state.copy(saving = false, error = true))
            }
        }
    }

    fun retry() {
        if (state.saving) return
        if (finished && latest != null) saveResult() else observe()
    }

    fun restart(): Boolean {
        if (!state.canRestart || (finished && !state.resultReady)) return false
        attemptId = UUID.randomUUID().toString()
        savedState["attempt_id"] = attemptId
        finished = false
        successful = false
        update(state.copy(resultReady = false, successful = false))
        return true
    }

    private fun update(value: MiniGameSessionUiState) {
        state = value
        onState(value)
    }
}
