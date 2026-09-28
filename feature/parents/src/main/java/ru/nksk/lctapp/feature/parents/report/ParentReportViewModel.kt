package ru.nksk.lctapp.feature.parents.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ParentReportViewModel @Inject constructor(private val repository: ParentReportRepository) : ViewModel() {
    private val reload = MutableStateFlow(0L)
    private val refreshMutex = Mutex()

    val uiState = reload.flatMapLatest {
        flow<ParentReportUiState> {
            emit(ParentReportUiState.Loading)
            repository.observeReport().collect { emit(ParentReportUiState.Ready(it)) }
        }.catch { emit(ParentReportUiState.Error) }
    }.stateIn(
        scope = viewModelScope,
        // Locking/backgrounding removes the subscriber and immediately stops history projection.
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        initialValue = ParentReportUiState.Loading,
    )

    fun retry() { reload.update { it + 1 } }

    /** Caller owns the unlocked screen lifecycle; cancellation also releases the request guard. */
    suspend fun refreshAssessments() {
        if (!refreshMutex.tryLock()) return
        try { repository.refreshAssessments() } finally { refreshMutex.unlock() }
    }
}
