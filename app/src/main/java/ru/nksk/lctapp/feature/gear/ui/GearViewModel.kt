package ru.nksk.lctapp.feature.gear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nksk.lctapp.domain.content.StoryContentRepository
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.R
import java.util.UUID

@HiltViewModel
internal class GearViewModel @Inject constructor(
    private val games: GameRepository,
    private val content: StoryContentRepository,
    private val session: GameSession,
) : ViewModel() {
    private val mutableState = MutableStateFlow<GearLoadState>(GearLoadState.Loading)
    val uiState: StateFlow<GearLoadState> = mutableState.asStateFlow()
    private var observation: Job? = null
    private var latest: GameState? = null
    private var changing = false

    init { retry() }

    fun retry() {
        if (observation?.isActive == true) return
        observation = viewModelScope.launch {
            mutableState.value = GearLoadState.Loading
            try {
                var projected: InventoryInput? = null
                var definitions: List<ItemDefinition>? = null
                games.observe().collect { saved ->
                    val game = checkNotNull(saved) { "Inventory requires an existing save" }
                    // Keep command guards fresh even when only a wallet/day changed.
                    latest = game
                    val input = InventoryInput(game.ownedItems, game.pet.name, game.pet.selectedLookId)
                    if (input == projected) return@collect
                    // Definitions are immutable. New ownership must read after its catalog install;
                    // changing an outfit or name can reuse the already validated definitions.
                    if (definitions == null || projected?.owned != input.owned) definitions = content.read().items
                    val inventory = gearUiState(input.owned, checkNotNull(definitions), input.name, input.look)
                    projected = input
                    mutableState.value = GearLoadState.Ready(inventory, busy = changing)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = GearLoadState.Error
            }
        }
    }

    fun equip(lookId: String) {
        if (changing) return
        val shown = latest ?: return
        val ready = mutableState.value as? GearLoadState.Ready ?: return
        changing = true
        mutableState.value = ready.copy(busy = true, actionMessage = null)
        viewModelScope.launch {
            var error: Int? = null
            try {
                val result = session.dispatch(EngineRequest(UUID.randomUUID().toString(), shown.engine?.revision,
                    EngineCommand.SetPetLook(lookId, shown.pet.selectedLookId)))
                if (result is EngineResult.Blocked) error = R.string.gear_equip_changed
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = R.string.gear_equip_error
            } finally {
                changing = false
                (mutableState.value as? GearLoadState.Ready)?.let {
                    mutableState.value = it.copy(busy = false, actionMessage = error)
                }
            }
        }
    }
}

private data class InventoryInput(val owned: List<OwnedItem>, val name: String, val look: String)
