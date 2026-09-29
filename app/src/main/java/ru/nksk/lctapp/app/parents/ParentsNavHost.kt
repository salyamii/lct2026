package ru.nksk.lctapp.app.parents

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import ru.nksk.lctapp.app.navigation.AppNavigator
import ru.nksk.lctapp.feature.parents.navigation.ParentQuestsRoute
import ru.nksk.lctapp.feature.parents.navigation.ParentShoppingQuestRoute
import ru.nksk.lctapp.feature.parents.quests.ParentQuestEntry
import ru.nksk.lctapp.feature.parents.quests.ParentQuest
import ru.nksk.lctapp.feature.parents.navigation.ParentQuestRoute
import ru.nksk.lctapp.feature.parents.navigation.ParentReportRoute
import ru.nksk.lctapp.feature.parents.navigation.ParentTopicRoute
import ru.nksk.lctapp.feature.parents.quests.ParentsQuestsScreen
import ru.nksk.lctapp.feature.parents.report.ParentReportEntry
import ru.nksk.lctapp.feature.parents.report.ParentReportRefreshEffect
import ru.nksk.lctapp.feature.parents.report.ParentTopicEntry

private val ParentNavigationState = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(ParentReportRoute::class, ParentReportRoute.serializer())
            subclass(ParentTopicRoute::class, ParentTopicRoute.serializer())
            subclass(ParentQuestsRoute::class, ParentQuestsRoute.serializer())
            subclass(ParentShoppingQuestRoute::class, ParentShoppingQuestRoute.serializer())
            subclass(ParentQuestRoute::class, ParentQuestRoute.serializer())
        }
    }
}

/** Called only while the Activity's PIN session is unlocked. */
@Composable
internal fun ParentsNavHost(onClose: () -> Unit) {
    ParentReportRefreshEffect()
    val backStack = rememberNavBackStack(ParentNavigationState, ParentReportRoute)
    val navigator = remember(backStack) { AppNavigator(backStack) }
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) navigator.goBack() else onClose() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ParentReportRoute> { source ->
                val close = dropUnlessResumed { if (backStack.lastOrNull() == source) onClose() }
                ParentReportEntry(
                    onOpenTopic = { id -> navigator.navigate(source, ParentTopicRoute(id)) },
                    onClose = close,
                    onOpenQuest = { quest -> navigator.navigate(source, ParentQuestRoute(quest)) },
                )
            }
            entry<ParentTopicRoute> { source ->
                ParentTopicEntry(
                    skillId = source.skillId,
                    onBack = dropUnlessResumed { navigator.goBack(source) },
                )
            }
            entry<ParentQuestRoute> { source ->
                ParentQuestEntry(source.quest, onBack = dropUnlessResumed { navigator.goBack(source) })
            }
            entry<ParentShoppingQuestRoute> { source ->
                ParentQuestEntry(ParentQuest.SHOPPING, onBack = dropUnlessResumed { navigator.goBack(source) })
            }
            entry<ParentQuestsRoute> { source ->
                ParentsQuestsScreen(onBack = dropUnlessResumed { navigator.goBack(source) })
            }
        },
    )
}
