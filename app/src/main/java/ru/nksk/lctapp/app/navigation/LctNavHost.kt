package ru.nksk.lctapp.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.delay
import ru.nksk.lctapp.core.ui.components.GameCompletionSnackbar
import ru.nksk.lctapp.core.ui.theme.AdventureNight
import ru.nksk.lctapp.feature.economy.navigation.Economy
import ru.nksk.lctapp.feature.economy.navigation.economyEntry
import ru.nksk.lctapp.feature.economy.navigation.Savings
import ru.nksk.lctapp.feature.economy.navigation.savingsEntry
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.day.navigation.dayEntry
import ru.nksk.lctapp.feature.gear.navigation.Gear
import ru.nksk.lctapp.feature.gear.navigation.gearEntry
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.goal.navigation.goalEntry
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.menu.navigation.mainMenuEntry
import ru.nksk.lctapp.feature.menu.ui.MainMenuAction
import ru.nksk.lctapp.feature.tasks.navigation.DifferencesGame
import ru.nksk.lctapp.feature.tasks.navigation.LightsGame
import ru.nksk.lctapp.feature.tasks.navigation.PipesGame
import ru.nksk.lctapp.feature.tasks.navigation.StarPlates
import ru.nksk.lctapp.feature.tasks.navigation.PriceCheck
import ru.nksk.lctapp.feature.tasks.navigation.SequenceGame
import ru.nksk.lctapp.feature.tasks.navigation.StackingGame
import ru.nksk.lctapp.feature.tasks.navigation.Telescope
import ru.nksk.lctapp.feature.tasks.navigation.Tasks
import ru.nksk.lctapp.feature.tasks.navigation.DeedGame
import ru.nksk.lctapp.feature.tasks.navigation.deedGameEntry
import ru.nksk.lctapp.feature.tasks.navigation.tasksEntry
import ru.nksk.lctapp.feature.tasks.ui.DeedsAction
import ru.nksk.lctapp.feature.map.navigation.GameMap
import ru.nksk.lctapp.feature.map.navigation.mapEntry
import ru.nksk.lctapp.feature.learning.navigation.Learning
import ru.nksk.lctapp.feature.learning.navigation.SkillTraining
import ru.nksk.lctapp.feature.learning.navigation.OtherPaths
import ru.nksk.lctapp.feature.learning.navigation.learningEntry
import ru.nksk.lctapp.feature.settings.navigation.Settings
import ru.nksk.lctapp.feature.settings.navigation.settingsEntry
import ru.nksk.lctapp.feature.settings.ui.SettingsGearButton

private const val NavigationTransitionMillis = 160

/** UI-only delivery identity: identical consecutive confirmations are still separate messages. */
private class CompletionNotice(val message: String)

/** Navigation composition root. Features own their keys and entries; screens receive callbacks. */
@Composable
fun LctNavHost(
    modifier: Modifier = Modifier,
    debugSettingsButton: (@Composable () -> Unit)? = null,
) {
    val backStack = rememberNavBackStack(AppNavigationSavedStateConfiguration, MainMenu)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val snackbarHost = remember { SnackbarHostState() }
    var completion by remember { mutableStateOf<CompletionNotice?>(null) }
    val gateModel: EconomyGateViewModel = hiltViewModel()
    val gate by gateModel.uiState.collectAsStateWithLifecycle()
    var presentedPlanningId by rememberSaveable { mutableStateOf<String?>(null) }
    val destination = backStack.lastOrNull()
    val pending = gate.planning
    LaunchedEffect(pending, destination) {
        if (shouldPresentBudget(pending, destination, presentedPlanningId)) {
            presentedPlanningId = checkNotNull(pending).id
            // Persisted budget has priority over restored gameplay routes; Back returns to the menu.
            backStack.clear()
            backStack.add(MainMenu)
            backStack.add(Economy)
        } else if (pending != null && destination == Economy) {
            presentedPlanningId = pending.id
        }
    }
    val redirectingToBudget = shouldPresentBudget(pending, destination, presentedPlanningId)
    if (gate.loading || gate.failed || redirectingToBudget) {
        Box(Modifier.fillMaxSize().background(AdventureNight), contentAlignment = Alignment.Center) {
            if (gate.loading || redirectingToBudget) CircularProgressIndicator()
            else Column {
                Text("Не удалось прочитать бюджет")
                Button(onClick = gateModel::retry) { Text("Повторить") }
            }
        }
        return
    }
    val finish: (NavKey, String?) -> Unit = { source, message ->
        if (backStack.lastOrNull() == source && backStack.size > 1) {
            navigator.returnToRoot(source)
            completion = message?.let(::CompletionNotice)
        }
    }
    LaunchedEffect(completion, destination) {
        val notice = completion ?: return@LaunchedEffect
        if (destination != MainMenu) {
            completion = null
            return@LaunchedEffect
        }
        delay(NavigationTransitionMillis.toLong())
        snackbarHost.showSnackbar(notice.message, duration = SnackbarDuration.Short)
        if (completion === notice) completion = null
    }

    BoxWithConstraints(modifier.fillMaxSize().background(AdventureNight)) {
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = backStack,
            onBack = navigator::goBack,
            // Keep illustrated screens opaque: the default crossfade blends their text and artwork.
            transitionSpec = {
                slideIntoContainer(SlideDirection.Start, tween(NavigationTransitionMillis)) togetherWith
                    slideOutOfContainer(SlideDirection.Start, tween(NavigationTransitionMillis))
            },
            popTransitionSpec = {
                slideIntoContainer(SlideDirection.End, tween(NavigationTransitionMillis)) togetherWith
                    slideOutOfContainer(SlideDirection.End, tween(NavigationTransitionMillis))
            },
            predictivePopTransitionSpec = {
                slideIntoContainer(SlideDirection.End, tween(NavigationTransitionMillis)) togetherWith
                    slideOutOfContainer(SlideDirection.End, tween(NavigationTransitionMillis))
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                mainMenuEntry(settingsButton = {
                    SettingsGearButton(dropUnlessResumed { navigator.navigate(MainMenu, Settings) })
                }) { source, action ->
                    navigator.navigate(
                        source = source,
                        destination = when (action) {
                            MainMenuAction.Gear -> Gear
                            MainMenuAction.Tasks -> Tasks
                            MainMenuAction.Goal -> Goal
                            MainMenuAction.Coins -> Economy
                            MainMenuAction.Finance -> Learning
                            MainMenuAction.Savings -> Savings
                            MainMenuAction.Village -> GameMap
                            MainMenuAction.ContinueDay, MainMenuAction.Feed -> if (pending != null) Economy else Day
                        },
                    )
                }
                settingsEntry(onBack = navigator::goBack, debugButton = debugSettingsButton)
                gearEntry(onBack = navigator::goBack)
                tasksEntry(
                    onEvent = { source -> navigator.navigate(source, Day) },
                    onGame = { source, id -> navigator.navigate(source, DeedGame(id)) },
                    onOpen = { source, action ->
                        navigator.navigate(source, when (action) {
                            DeedsAction.StarPlates -> StarPlates
                            DeedsAction.PriceCheck -> PriceCheck
                            DeedsAction.Telescope -> Telescope
                            DeedsAction.SkillTraining -> SkillTraining
                            DeedsAction.Lights -> LightsGame
                            DeedsAction.Sequence -> SequenceGame
                            DeedsAction.Pipes -> PipesGame
                            DeedsAction.Differences -> DifferencesGame
                            DeedsAction.Stacking -> StackingGame
                        })
                    },
                    onBack = navigator::goBack,
                )
                goalEntry(onBack = navigator::goBack,
                    onOpenSavings = { source -> navigator.navigateToExisting(source, Savings) },
                    onReturnHome = navigator::returnToRoot)
                economyEntry(onBack = navigator::returnToRoot, onConfirmed = navigator::returnToRoot,
                    onOpenSavings = { source -> navigator.navigateToExisting(source, Savings) })
                savingsEntry(onBack = navigator::goBack,
                    onOpenGoal = { source -> navigator.navigateToExisting(source, Goal) },
                    onOpenBudget = { source -> navigator.navigateToExisting(source, Economy) })
                learningEntry(onBack = navigator::goBack,
                    onOpenBudget = { source -> navigator.navigateToExisting(source, Economy) })
                mapEntry(onBack = navigator::goBack, onSelected = navigator::returnToRoot)
                dayEntry(onBack = navigator::goBack, onFinished = finish,
                    onGame = { source, id -> navigator.replace(source, DeedGame(id)) },
                    onStoryGame = { source, id, choice -> navigator.replace(source, DeedGame(id, choice)) },
                    onLearning = { source -> navigator.navigate(source, SkillTraining) },
                    onReflection = { source, day -> navigator.navigate(source, OtherPaths(day)) })
                deedGameEntry(onFinished = finish)
            },
        )
        if (destination == MainMenu) {
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding()
                    .padding(top = if (maxWidth > maxHeight || maxWidth >= 840.dp) 12.dp else 112.dp),
                snackbar = { GameCompletionSnackbar(it) },
            )
        }
    }
}
