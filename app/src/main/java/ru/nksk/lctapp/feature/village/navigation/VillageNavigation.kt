package ru.nksk.lctapp.feature.village.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen
import ru.nksk.lctapp.ui.village.navigation.Village as SavedVillage

typealias Village = SavedVillage

fun EntryProviderScope<NavKey>.villageEntry(onBack: (Village) -> Unit) {
    entry<Village> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_village,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
