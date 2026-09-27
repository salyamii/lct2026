package ru.nksk.lctapp.feature.gear.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.gear.ui.GearScreen
import ru.nksk.lctapp.feature.gear.ui.GearViewModel
import ru.nksk.lctapp.feature.gear.ui.GearLoadState
import ru.nksk.lctapp.feature.gear.ui.GearItemDetails

@Serializable
@SerialName("gear")
data object Gear : NavKey

fun EntryProviderScope<NavKey>.gearEntry(onBack: (Gear) -> Unit) {
    entry<Gear> { source ->
        val model = hiltViewModel<GearViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val gridState = rememberLazyGridState()
        var selectedOccurrenceId by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedPageId by rememberSaveable { mutableStateOf<String?>(null) }
        val ready = state as? GearLoadState.Ready
        val selectedItem = ready?.inventory?.ownedOccurrence(selectedOccurrenceId)
        val closeDetails: () -> Unit = { selectedOccurrenceId = null; selectedPageId = null }
        LaunchedEffect(ready?.inventory, selectedOccurrenceId) {
            // A restored local page never grants ownership or exposes a catalog-only item.
            if (ready != null && selectedOccurrenceId != null && selectedItem == null) closeDetails()
        }
        BackHandler(enabled = selectedItem != null) { closeDetails() }
        if (selectedItem != null) {
            GearItemDetails(selectedItem, selectedPageId,
                onPageSelected = { page ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        selectedItem.pages.any { it.id == page }) selectedPageId = page
                },
                onClose = dropUnlessResumed { closeDetails() },
                onEquip = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.equip(it) },
                busy = ready?.busy == true,
                actionMessage = ready?.actionMessage)
        } else {
            GearScreen(
                state = state,
                onRetry = model::retry,
                onBack = dropUnlessResumed { onBack(source) },
                onEquip = { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.equip(it) },
                onOpenItem = { id ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        ready?.inventory?.ownedOccurrence(id) != null) {
                        selectedOccurrenceId = id
                        selectedPageId = null
                    }
                },
                gridState = gridState,
            )
        }
    }
}
