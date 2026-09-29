package ru.nksk.lctapp.feature.parents.quests

import androidx.compose.runtime.Immutable
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
import ru.nksk.lctapp.domain.backend.ParentQuestRewardsRepository
import ru.nksk.lctapp.domain.backend.ParentQuestRewardException
import ru.nksk.lctapp.domain.backend.PendingParentQuestReward
import ru.nksk.lctapp.domain.pet.ParentRewardCaps

@Immutable
data class ParentQuestUiState(
    val checkedSteps: List<Boolean> = List(4) { false },
    val completed: Boolean = false,
    val selectedItemId: String = ParentRewardCaps.all.first().itemId,
    val ownedItemIds: Set<String> = emptySet(),
    val gameRunId: String? = null,
    val pending: PendingParentQuestReward? = null,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val targetChanged: Boolean = false,
    val error: String? = null,
) {
    val completedSteps: Int get() = checkedSteps.count { it }
    val selectionLocked: Boolean get() = !ready || busy || completed || pending != null || targetChanged
    val selectedOwned: Boolean get() = selectedItemId in ownedItemIds
    val canComplete: Boolean get() = ready && !busy && !completed && !targetChanged && gameRunId != null &&
        checkedSteps.all { it } && (!selectedOwned || pending != null)
}

@HiltViewModel
class ParentQuestViewModel @Inject constructor(private val rewards: ParentQuestRewardsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ParentQuestUiState())
    val uiState = mutableState.asStateFlow()
    private var quest: ParentQuest? = null
    private var observer: Job? = null

    fun load(value: ParentQuest) {
        if (quest == value && observer?.isActive == true) return
        quest = value
        observer?.cancel()
        observer = viewModelScope.launch {
            mutableState.update { it.copy(ready = false, error = null) }
            try {
                val pending = rewards.pending(value.name)
                if (pending != null) mutableState.update { it.copy(pending = pending,
                    selectedItemId = pending.itemId, gameRunId = pending.gameRunId,
                    checkedSteps = List(4) { true }) }
                rewards.inventory().collect { inventory ->
                    mutableState.update { state ->
                        val changed = state.gameRunId != null && state.gameRunId != inventory?.gameRunId
                        val firstChoice = if (state.gameRunId == null && inventory != null)
                            ParentRewardCaps.all.firstOrNull { it.itemId !in inventory.ownedItemIds }?.itemId
                        else null
                        state.copy(ready = inventory != null, ownedItemIds = inventory?.ownedItemIds.orEmpty(),
                            gameRunId = state.gameRunId ?: inventory?.gameRunId,
                            selectedItemId = firstChoice ?: state.selectedItemId,
                            targetChanged = changed,
                            error = if (changed) "Прохождение изменилось. Вернитесь к списку квестов." else
                                if (inventory == null) "Сначала начните игру." else state.error)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(ready = false, error = "Не удалось загрузить инвентарь. Попробуйте ещё раз.") } }
        }
    }

    fun selectItem(itemId: String) {
        if (ParentRewardCaps.forItem(itemId) == null) return
        mutableState.update { if (it.selectionLocked) it else it.copy(selectedItemId = itemId, error = null) }
    }

    fun setStepChecked(index: Int, checked: Boolean) {
        mutableState.update { state ->
            if (state.selectionLocked) state else state.copy(
                checkedSteps = state.checkedSteps.mapIndexed { step, value -> if (step == index) checked else value })
        }
    }

    fun complete() {
        val state = mutableState.value
        val currentQuest = quest ?: return
        if (!state.canComplete) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                rewards.issue(currentQuest.name, state.selectedItemId, checkNotNull(state.gameRunId))
                mutableState.update { it.copy(completed = true, busy = false, pending = null, error = null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                // Even an uncertain HTTP response freezes selection. The durable request
                // is the source of truth after reopening or process death.
                val pending = try { rewards.pending(currentQuest.name) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { PendingParentQuestReward(checkNotNull(state.gameRunId), state.selectedItemId) }
                mutableState.update { it.copy(busy = false, pending = pending,
                    error = (failure as? ParentQuestRewardException)?.message ?:
                        "Не удалось подтвердить выдачу и обновить инвентарь. Проверьте интернет и повторите — вторая награда не создастся.") }
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
