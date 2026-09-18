package ru.nksk.lctapp.feature.tasks.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.tasks.DeedsHost

@Serializable
@SerialName("tasks")
data object Tasks : NavKey

/** «Дела» — deeds section where the player earns bonus coins. */
fun EntryProviderScope<NavKey>.tasksEntry(onBack: (Tasks) -> Unit) {
    entry<Tasks> { source ->
        DeedsHost(onExit = dropUnlessResumed { onBack(source) })
    }
}
