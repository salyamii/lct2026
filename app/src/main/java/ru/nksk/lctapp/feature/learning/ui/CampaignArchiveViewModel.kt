package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.core.ui.game.AdventurePetPresentation
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.history.ArchivedGameRunSummary
import ru.nksk.lctapp.domain.history.CampaignRestartConflictException
import ru.nksk.lctapp.domain.history.CampaignRestartRequest

internal data class CampaignArchiveUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val canRestart: Boolean = false,
    val restarted: Boolean = false,
    val pet: AdventurePetPresentation? = null,
    val archives: List<ArchivedGameRunSummary> = emptyList(),
    val detail: HistoryUiState? = null,
)

/** A rewind is one explicit persisted operation; archive viewing never restores or changes the world. */
@HiltViewModel
internal class CampaignArchiveViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow(CampaignArchiveUiState())
    val uiState = state.asStateFlow()
    private var pendingRestart: CampaignRestartRequest? = null

    init { reload() }

    fun retry() {
        if (pendingRestart != null) restart() else reload()
    }

    fun reload() {
        if (state.value.busy) return
        state.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = session.exportSnapshot()
                val archives = session.archivedRuns()
                state.value = CampaignArchiveUiState(loading = false,
                    canRestart = session.canRestartCampaign(snapshot.state),
                    pet = snapshot.state.pet.toAdventurePetPresentation(showReaction = false), archives = archives)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, error = "Не удалось прочитать историю. Попробуй ещё раз.") }
        }
    }

    fun openArchive(runId: String) {
        if (state.value.busy) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = checkNotNull(session.archivedRun(runId))
                val detail = withContext(Dispatchers.Default) {
                    historyPresentation(snapshot.state, snapshot.history, session.catalog, historyRowLimit = Int.MAX_VALUE)
                }
                state.value = state.value.copy(detail = detail)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(error = "Не удалось открыть прошлое приключение. Попробуй ещё раз.") }
            finally { state.value = state.value.copy(busy = false) }
        }
    }

    fun closeArchive() {
        if (!state.value.busy) state.value = state.value.copy(detail = null, error = null)
    }

    fun restart() {
        if (state.value.busy || !state.value.canRestart || state.value.restarted) return
        state.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val request = pendingRestart ?: session.exportSnapshot().let { snapshot ->
                    check(session.canRestartCampaign(snapshot.state))
                    CampaignRestartRequest(UUID.randomUUID().toString(), snapshot.runId,
                        snapshot.state.engine?.revision, snapshot.historySequence).also { pendingRestart = it }
                }
                session.restartCampaign(request)
                pendingRestart = null
                state.value = state.value.copy(restarted = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: CampaignRestartConflictException) {
                pendingRestart = null
                state.value = state.value.copy(canRestart = false,
                    error = "Приключение изменилось. Обнови страницу перед возвращением в начало.")
            } catch (_: Exception) {
                state.value = state.value.copy(error = "Не удалось подтвердить возвращение. Повтори попытку — история сохранится один раз.")
            } finally { state.value = state.value.copy(busy = false) }
        }
    }
}
