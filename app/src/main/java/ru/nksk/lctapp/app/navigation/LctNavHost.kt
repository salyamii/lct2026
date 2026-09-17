package ru.nksk.lctapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ru.nksk.lctapp.feature.coins.navigation.Coins
import ru.nksk.lctapp.feature.coins.navigation.coinsEntry
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.day.navigation.dayEntry
import ru.nksk.lctapp.feature.gear.navigation.Gear
import ru.nksk.lctapp.feature.gear.navigation.gearEntry
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.goal.navigation.goalEntry
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.menu.navigation.mainMenuEntry
import ru.nksk.lctapp.feature.menu.ui.MainMenuAction
import ru.nksk.lctapp.feature.tasks.navigation.Tasks
import ru.nksk.lctapp.feature.tasks.navigation.tasksEntry
import ru.nksk.lctapp.feature.village.navigation.Village
import ru.nksk.lctapp.feature.village.navigation.villageEntry

/** Navigation composition root. Features own their keys and entries; screens receive callbacks. */
@Composable
fun LctNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(MainMenu)
    val navigator = remember(backStack) { AppNavigator(backStack) }

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = navigator::goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            mainMenuEntry { source, action ->
                navigator.navigate(
                    source = source,
                    destination = when (action) {
                        MainMenuAction.Gear -> Gear
                        MainMenuAction.Tasks -> Tasks
                        MainMenuAction.Goal -> Goal
                        MainMenuAction.Coins -> Coins
                        MainMenuAction.Village -> Village
                        MainMenuAction.ContinueDay -> Day
                    },
                )
            }
            gearEntry(onBack = navigator::goBack)
            tasksEntry(onBack = navigator::goBack)
            goalEntry(onBack = navigator::goBack)
            coinsEntry(onBack = navigator::goBack)
            villageEntry(onBack = navigator::goBack)
            dayEntry(onBack = navigator::goBack)
        },
    )
}
