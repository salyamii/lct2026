package ru.nksk.lctapp.feature.gear.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen

@Serializable
@SerialName("gear")
data object Gear : NavKey

fun EntryProviderScope<NavKey>.gearEntry(onBack: (Gear) -> Unit) {
    entry<Gear> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_gear,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
