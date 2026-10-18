package ru.nksk.lctapp.feature.storygames.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.nksk.lctapp.feature.storygames.ui.BoatGameEntry
import ru.nksk.lctapp.feature.storygames.ui.ChapterGamesScreen
import ru.nksk.lctapp.feature.storygames.ui.ChapterTestGame
import ru.nksk.lctapp.feature.storygames.ui.MarkGameEntry
import ru.nksk.lctapp.feature.storygames.ui.SignalGameEntry
import ru.nksk.lctapp.feature.storygames.ui.StarChartGameEntry
import ru.nksk.lctapp.feature.storygames.ui.TelescopeGameEntry
import ru.nksk.lctapp.feature.storygames.ui.WheelGameEntry
import ru.nksk.lctapp.feature.storygames.ui.StrapGameEntry

@Serializable
@SerialName("chapter_test_games")
data object ChapterTestGames : NavKey

@Serializable
@SerialName("chapter_test_game")
data class ChapterTestGameSession(val gameId: String) : NavKey

fun EntryProviderScope<NavKey>.storyGamesEntry(
    onOpen: (NavKey, String) -> Unit,
    onBack: (NavKey) -> Unit,
) {
    entry<ChapterTestGames> { source ->
        ChapterGamesScreen(
            onOpen = { id -> if (id in ChapterTestGame.entries.map { it.id }) onOpen(source, id) },
            onExit = dropUnlessResumed { onBack(source) },
        )
    }
    entry<ChapterTestGameSession> { source ->
        val onExit = dropUnlessResumed { onBack(source) }
        when (source.gameId) {
            ChapterTestGame.TELESCOPE.id -> TelescopeGameEntry(onExit)
            ChapterTestGame.STAR_CHART.id -> StarChartGameEntry(onExit)
            ChapterTestGame.MARK.id -> MarkGameEntry(onExit)
            ChapterTestGame.WHEEL.id -> WheelGameEntry(onExit)
            ChapterTestGame.STRAP.id -> StrapGameEntry(onExit)
            ChapterTestGame.BOAT.id -> BoatGameEntry(onExit)
            ChapterTestGame.SIGNAL.id -> SignalGameEntry(onExit)
            else -> ChapterGamesScreen(onOpen = { onOpen(source, it) }, onExit = onExit)
        }
    }
}
