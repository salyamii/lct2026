package ru.nksk.lctapp.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import ru.nksk.lctapp.data.backend.BackendSyncScheduler
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.backend.CloudSyncRepository
import javax.inject.Inject

/** App-owned scheduling stays outside features and does not change the game. */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class BackendSyncViewModel @Inject constructor(
    private val scheduler: BackendSyncScheduler,
    session: GameSession,
    private val cloud: CloudSyncRepository,
) : ViewModel() {
    init {
        scheduler.ensurePeriodicSync()
        viewModelScope.launch {
            session.observeHistorySequence().distinctUntilChanged()
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException || cause !is Exception) throw cause
                    delay(5_000L * (attempt + 1).coerceAtMost(12))
                    true
                }
                .debounce(HistorySyncDebounceMillis)
                .collect { scheduler.requestSync() }
        }
    }

    suspend fun whileResumed() {
        try {
            runForegroundBackendSync(scheduler.networkReturns(), scheduler::isConnected,
                cloud::synchronize, scheduler::requestSync)
        } finally {
            // Continue a cancelled/unfinished exchange after the app leaves the foreground.
            scheduler.requestSync()
        }
    }
}
