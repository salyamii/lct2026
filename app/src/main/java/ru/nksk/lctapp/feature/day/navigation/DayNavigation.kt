package ru.nksk.lctapp.feature.day.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen

@Serializable
@SerialName("day")
data object Day : NavKey

fun EntryProviderScope<NavKey>.dayEntry(onBack: (Day) -> Unit) {
    entry<Day> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_continue,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
