package ru.nksk.lctapp.feature.coins.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.FeaturePlaceholderScreen
import ru.nksk.lctapp.ui.coins.navigation.Coins as SavedCoins

typealias Coins = SavedCoins

fun EntryProviderScope<NavKey>.coinsEntry(onBack: (Coins) -> Unit) {
    entry<Coins> { source ->
        FeaturePlaceholderScreen(
            titleRes = R.string.menu_coins,
            onBack = dropUnlessResumed { onBack(source) },
        )
    }
}
