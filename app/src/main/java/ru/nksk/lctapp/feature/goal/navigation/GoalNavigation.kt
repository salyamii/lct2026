package ru.nksk.lctapp.feature.goal.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen

@Serializable
@SerialName("goal")
data object Goal : NavKey

fun EntryProviderScope<NavKey>.goalEntry(onBack: (Goal) -> Unit) {
    entry<Goal> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_goal,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
