package ru.nksk.lctapp.feature.learning.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.learning.ui.CampaignArchiveScreen
import ru.nksk.lctapp.feature.learning.ui.CampaignArchiveViewModel

@Serializable
@SerialName("campaign_archive")
data object CampaignArchive : NavKey

fun EntryProviderScope<NavKey>.campaignArchiveEntry(onBack: (NavKey) -> Unit, onRestarted: (NavKey) -> Unit) {
    entry<CampaignArchive> { source ->
        val model = hiltViewModel<CampaignArchiveViewModel>()
        val state by model.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.restarted) { if (state.restarted) onRestarted(source) }
        CampaignArchiveScreen(state, onRestart = dropUnlessResumed { model.restart() },
            onArchive = model::openArchive, onCloseArchive = model::closeArchive,
            onRetry = model::reload, onBack = dropUnlessResumed { onBack(source) })
    }
}
