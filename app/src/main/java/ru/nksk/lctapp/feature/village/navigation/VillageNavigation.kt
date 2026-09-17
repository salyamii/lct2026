package ru.nksk.lctapp.feature.village.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen

@Serializable
@SerialName("village")
data object Village : NavKey

fun EntryProviderScope<NavKey>.villageEntry(onBack: (Village) -> Unit) {
    entry<Village> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_village,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
