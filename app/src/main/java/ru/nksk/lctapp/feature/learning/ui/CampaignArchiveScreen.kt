package ru.nksk.lctapp.feature.learning.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.nksk.lctapp.core.ui.components.AdventureBody
import ru.nksk.lctapp.core.ui.components.MovingPetArtwork

@Composable
internal fun CampaignArchiveScreen(state: CampaignArchiveUiState, onRestart: () -> Unit,
    onArchive: (String) -> Unit, onCloseArchive: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    BackHandler(state.busy) {}
    val detail = state.detail
    if (detail != null) {
        BackHandler { onCloseArchive() }
        HistoryScreen(detail, onRetry = onRetry, onBack = onCloseArchive, title = "Прошлое приключение")
        return
    }
    LearningPage("Хроноскоп", onBack, backEnabled = !state.busy) {
        learningStatus(state.loading, false, state.error, onRetry = onRetry)
        if (!state.loading && state.canRestart) item {
            LearningCard("Вернёмся к началу?") {
                state.pet?.artworkRes?.let { image ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MovingPetArtwork(image, state.pet.name, modifier = Modifier.size(180.dp))
                    }
                }
                AdventureBody("Хроноскоп вернёт ${state.pet?.name.orEmpty()} в детство. Ещё раз пройдём знакомую историю и попробуем другие решения.")
                AdventureBody("Монеты, покупки и задания начнутся заново. Всё прошлое приключение останется здесь — его можно будет перечитать.")
                PracticeButton(if (state.error == null) "Вернуться в начало" else "Повторить возвращение", !state.busy, primary = true, onClick = onRestart)
            }
        }
        if (!state.loading && state.archives.isEmpty()) item {
            LearningCard("Прошлые приключения") {
                AdventureBody("После возвращения в начало здесь сохранится история завершённого пути.")
            }
        }
        itemsIndexed(state.archives, key = { _, archive -> archive.runId }) { index, archive ->
            LearningCard("Приключение ${index + 1}") {
                AdventureBody("${archive.petName} · ${archive.finalDay?.let { "до $it-го дня" } ?: "история завершена"}")
                PracticeButton("Открыть историю", !state.busy, primary = false) { onArchive(archive.runId) }
            }
        }
    }
}
