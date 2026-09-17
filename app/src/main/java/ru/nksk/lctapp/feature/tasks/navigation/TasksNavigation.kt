package ru.nksk.lctapp.feature.tasks.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen
import ru.nksk.lctapp.ui.tasks.navigation.Tasks as SavedTasks

typealias Tasks = SavedTasks

fun EntryProviderScope<NavKey>.tasksEntry(onBack: (Tasks) -> Unit) {
    entry<Tasks> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_tasks,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
