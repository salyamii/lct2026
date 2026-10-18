package ru.nksk.lctapp.feature.settings.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.BuildConfig
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.GameActionButton
import ru.nksk.lctapp.core.ui.components.GameActionStyle
import ru.nksk.lctapp.core.ui.components.GameInk
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.feature.settings.ui.SettingsCard
import ru.nksk.lctapp.feature.settings.ui.CloudSettingsCard
import ru.nksk.lctapp.feature.settings.ui.SettingsAction
import ru.nksk.lctapp.feature.settings.ui.SettingsUiState

@Composable
internal fun SettingsDebugInfo(settings: SettingsUiState, onAction: (SettingsAction) -> Unit,
    onCopy: (String) -> Unit) {
    if (!BuildConfig.JURY_TOOLS) {
        val model = hiltViewModel<SettingsDebugInfoViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
        GameRunIdCard(state, onCopy, model::refresh)
    }
    CloudSettingsCard(settings.cloud,
        configured = settings.backendConfigured && !settings.loading && !settings.profileError,
        onAction = onAction)
}

@Composable
private fun GameRunIdCard(state: DebugGameRunUiState, onCopy: (String) -> Unit, onRetry: () -> Unit) {
    SettingsCard {
        Text("ID прохождения (gameRunId)", color = GameInk,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val runId = state.runId
        when {
            state.loading -> AdventureBody("Читаем ID прохождения…")
            state.error -> {
                AdventureBody("Не удалось прочитать ID прохождения.")
                GameActionButton("Повторить", onRetry, style = GameActionStyle.SECONDARY)
            }
            runId == null -> AdventureBody("Игра ещё не создана.")
            else -> {
                var copied by rememberSaveable(runId) { mutableStateOf(false) }
                Text(runId, color = GameInk, style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace)
                GameActionButton(if (copied) "Скопировано" else "Скопировать ID",
                    { onCopy(runId); copied = true }, style = GameActionStyle.SECONDARY)
            }
        }
    }
}

internal data class DebugGameRunUiState(
    val loading: Boolean = true,
    val runId: String? = null,
    val error: Boolean = false,
)

@HiltViewModel
internal class SettingsDebugInfoViewModel @Inject constructor(
    private val games: GameRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DebugGameRunUiState())
    val uiState = mutableState.asStateFlow()
    private var request: Job? = null

    fun refresh() {
        if (request?.isActive == true) return
        mutableState.value = DebugGameRunUiState()
        request = viewModelScope.launch {
            try {
                mutableState.value = DebugGameRunUiState(loading = false, runId = games.readSnapshotHead()?.runId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = DebugGameRunUiState(loading = false, error = true)
            }
        }
    }
}
