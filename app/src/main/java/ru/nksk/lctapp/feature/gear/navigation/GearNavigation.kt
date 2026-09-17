package ru.nksk.lctapp.feature.gear.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen
import ru.nksk.lctapp.ui.gear.navigation.Gear as SavedGear

typealias Gear = SavedGear

fun EntryProviderScope<NavKey>.gearEntry(onBack: (Gear) -> Unit) {
    entry<Gear> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_gear,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
