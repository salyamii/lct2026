package ru.nksk.lctapp.feature.economy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.economy.*
import ru.nksk.lctapp.domain.game.GameRepository

internal data class EconomyUiState(
    val loading: Boolean = true,
    val economy: EconomyState? = null,
    val saving: Boolean = false,
    val error: String? = null,
)

internal sealed interface EconomyAction {
    data object StartAllocation : EconomyAction
    data class Adjust(val article: BudgetArticle, val increase: Boolean) : EconomyAction
    data class SetAmount(val article: BudgetArticle, val amount: Long) : EconomyAction
    data object Confirm : EconomyAction
    data object Retry : EconomyAction
    data object DismissError : EconomyAction
}

/** Room owns both the planning step and draft amounts; no navigation key contains a game snapshot. */
@HiltViewModel
internal class EconomyViewModel @Inject constructor(private val games: GameRepository) : ViewModel() {
    private val state = MutableStateFlow(EconomyUiState())
    val uiState = state.asStateFlow()
    private val completions = Channel<Unit>(Channel.BUFFERED)
    val completed = completions.receiveAsFlow()
    private data class Pending(val action: EconomyAction, val id: String, val revision: Long, val manual: Boolean = false)
    private val actions = Channel<Pending>(Channel.UNLIMITED)
    private var observation: Job? = null
    private var failed: Pending? = null
    private var queued = 0
    private val manualId = "manual:" + UUID.randomUUID().toString()
    private var completedSessionId: String? = null

    init {
        observe()
        viewModelScope.launch {
            for (pending in actions) {
                if (failed != null) {
                    queued--
                    state.value = state.value.copy(saving = queued > 0)
                    continue
                }
                save(pending)
            }
        }
    }

    private fun observe() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch {
            try {
                games.observe().collect { game ->
                    checkNotNull(game) { "Game is not initialized" }
                    val incoming = game.economy.planning
                    val displayed = state.value.economy?.planning
                    // A delayed observer emission must not replace the result of our committed write.
                    if (incoming != null && (incoming.id == completedSessionId ||
                        (incoming.id == displayed?.id && incoming.revision < displayed.revision))) return@collect
                    state.value = state.value.copy(loading = false, economy = game.economy)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                state.value = state.value.copy(loading = false, error = "Не удалось прочитать бюджет. Попробуй ещё раз.")
            }
        }
    }

    fun onAction(action: EconomyAction) {
        if (action == EconomyAction.DismissError) {
            failed = null
            state.value = state.value.copy(error = null)
            return
        }
        if (action == EconomyAction.Retry) {
            val retry = failed
            failed = null
            state.value = state.value.copy(error = null)
            if (retry != null) enqueue(retry) else observe()
            return
        }
        if (state.value.error != null) return
        val economy = state.value.economy ?: return
        val planning = economy.planning
        if (planning == null) {
            if (action == EconomyAction.Confirm) {
                viewModelScope.launch { completions.send(Unit) }
            } else if (action is EconomyAction.Adjust || action is EconomyAction.SetAmount) {
                enqueue(Pending(action, manualId, 0, manual = true))
            }
        } else enqueue(Pending(action, planning.id, planning.revision))
    }

    private fun enqueue(pending: Pending) {
        queued++
        state.value = state.value.copy(saving = true)
        actions.trySend(pending)
    }

    private suspend fun save(pending: Pending) {
        state.value = state.value.copy(saving = true, error = null)
        try {
            val committed = games.update { current ->
                val economy = if (pending.manual && current.economy.planning == null)
                    EconomyOperations.beginManual(current.economy, pending.id) else current.economy
                // Relative button presses operate sequentially on the latest committed amounts.
                val planning = economy.planning
                val revision = if (pending.action is EconomyAction.Adjust && planning?.id == pending.id)
                    planning.revision else pending.revision
                val next = when (val action = pending.action) {
                    EconomyAction.StartAllocation -> EconomyOperations.startAllocation(economy, pending.id, revision)
                    is EconomyAction.Adjust -> EconomyOperations.adjustAllocation(economy, pending.id, revision,
                        BudgetSection.valueOf(action.article.name), action.increase)
                    is EconomyAction.SetAmount -> EconomyOperations.setAllocation(economy, pending.id, revision,
                        BudgetSection.valueOf(action.article.name), action.amount)
                    EconomyAction.Confirm -> EconomyOperations.confirm(economy, pending.id, revision)
                    else -> economy
                }
                current.copy(economy = next, engine = current.engine?.let {
                    it.copy(revision = Math.addExact(it.revision, 1L))
                })
            }
            if (pending.action == EconomyAction.Confirm) completedSessionId = pending.id
            state.value = state.value.copy(economy = committed.economy)
            if (pending.action == EconomyAction.Confirm) completions.send(Unit)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalArgumentException) {
            state.value = state.value.copy(error = "Бюджет изменился или сумма недоступна. Проверь распределение.")
        } catch (_: Exception) {
            failed = pending
            state.value = state.value.copy(error = "Не удалось сохранить бюджет. Повтори попытку.")
        } finally {
            queued--
            state.value = state.value.copy(saving = queued > 0)
        }
    }
}
