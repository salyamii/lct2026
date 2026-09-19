package ru.nksk.lctapp.feature.gear.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.gear.ui.GearScreen
import ru.nksk.lctapp.feature.gear.ui.GearViewModel

@Serializable
@SerialName("gear")
data object Gear : NavKey

fun EntryProviderScope<NavKey>.gearEntry(onBack: (Gear) -> Unit) {
    entry<Gear> { source ->
        val model = hiltViewModel<GearViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        GearScreen(
            state = state,
            onRetry = model::retry,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
