package ru.nksk.lctapp.feature.storygames.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.GameArtwork
import ru.nksk.lctapp.core.ui.components.GamePlaque
import ru.nksk.lctapp.core.ui.theme.Nunito

internal enum class ChapterTestGame(val id: String, val title: String, @DrawableRes val art: Int) {
    TELESCOPE("telescope", "Телескоп", R.drawable.collection_stars_telescope),
    STAR_CHART("star_chart", "Карта звёзд", R.drawable.collection_stars_map),
    MARK("mark", "Знак на причале", R.drawable.story_marked_crate),
    WHEEL("wheel", "Колесо телеги", R.drawable.story_game_wheel),
    STRAP("strap", "Пришить лямку", R.drawable.event_backpack_torn),
    BOAT("boat", "Поездка по реке", R.drawable.story_game_boat),
    SIGNAL("signal", "Ответ башни", R.drawable.story_north_tower_signal),
}

@Composable
internal fun ChapterGamesScreen(onOpen: (String) -> Unit, onExit: () -> Unit) {
    ChapterGameScaffold(
        title = "Тестовые игры",
        instruction = "Глава 1. Башня отвечает",
        onExit = onExit,
        footer = {
            Text("Можно пробовать снова. Монеты и сюжет сохранятся.",
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                color = Color(0xFFF4E3BA), fontFamily = Nunito,
                fontSize = 13.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
        },
    ) {
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ChapterTestGame.entries, key = { it.id }) { game ->
                GamePlaque(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        contentDescription = game.title
                    }.clickable(role = Role.Button) { onOpen(game.id) },
                    light = true, fixedEdges = true,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        GameArtwork(game.art, null, Modifier.fillMaxWidth().height(82.dp))
                        Text(game.title, Modifier.fillMaxWidth(),
                            fontFamily = Nunito, fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF25203C), fontSize = 15.sp, lineHeight = 19.sp,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
