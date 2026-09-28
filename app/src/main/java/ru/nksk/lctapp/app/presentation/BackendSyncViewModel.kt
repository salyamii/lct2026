package ru.nksk.lctapp.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.nksk.lctapp.data.backend.BackendSyncScheduler
import ru.nksk.lctapp.domain.engine.GameSession
import javax.inject.Inject

/** App-owned scheduling stays outside features and does not change the game. */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class BackendSyncViewModel @Inject constructor(
    private val scheduler: BackendSyncScheduler,
    session: GameSession,
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
                .debounce(5_000L)
                .collect { scheduler.requestSync() }
        }
    }

    suspend fun whileResumed(): Unit = coroutineScope {
        scheduler.requestSync()
        launch {
            scheduler.networkReturns().catch { cause ->
                if (cause is CancellationException || cause !is RuntimeException) throw cause
                // A platform callback limit must not cancel the foreground timer or the UI.
                // Connected WorkManager requests and the next resume remain available.
            }.collect { scheduler.requestSync() }
        }
        while (currentCoroutineContext().isActive) {
            delay(60_000L)
            scheduler.requestSync()
        }
    }
}
