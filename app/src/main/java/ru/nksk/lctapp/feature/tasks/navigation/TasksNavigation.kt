package ru.nksk.lctapp.feature.tasks.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen

@Serializable
@SerialName("tasks")
data object Tasks : NavKey

fun EntryProviderScope<NavKey>.tasksEntry(onBack: (Tasks) -> Unit) {
    entry<Tasks> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_tasks,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
