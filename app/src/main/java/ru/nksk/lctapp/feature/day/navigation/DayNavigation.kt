package ru.nksk.lctapp.feature.day.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen
import ru.nksk.lctapp.ui.day.navigation.Day as SavedDay

typealias Day = SavedDay

fun EntryProviderScope<NavKey>.dayEntry(onBack: (Day) -> Unit) {
    entry<Day> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_continue,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
