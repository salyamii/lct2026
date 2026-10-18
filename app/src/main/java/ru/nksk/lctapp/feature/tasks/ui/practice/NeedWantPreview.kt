package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.minigame.NeedWantQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.domain.minigame.PracticeGames

// Android Studio → Split/Design → Start Interactive Mode.
// The real screen and domain reducer run locally, with no Hilt, navigation or game save.
@Preview(name = "Нужда или желание · играть", group = "Нужда или желание", widthDp = 393, heightDp = 852)
@Composable
private fun NeedWantInteractivePreview() = NeedWantPreviewContent()

@Preview(name = "Верный ответ", group = "Нужда или желание", widthDp = 393, heightDp = 852)
@Composable
private fun NeedWantCorrectPreview() {
    val initial = PracticeGames.needs()
    NeedWantPreviewContent(initial.reduce(PracticeChoiceAction.Answer(initial.question.correctChoice, 0)))
}

@Preview(name = "Ошибка · 320 dp", group = "Нужда или желание", widthDp = 320, heightDp = 640)
@Preview(name = "Ошибка · крупный шрифт", group = "Нужда или желание", widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun NeedWantErrorPreview() {
    val initial = PracticeGames.needs()
    NeedWantPreviewContent(initial.reduce(PracticeChoiceAction.Answer(
        (initial.question.correctChoice + 1) % initial.question.choiceCount, 0)))
}

@Preview(name = "Итог · повтор", group = "Нужда или желание", widthDp = 393, heightDp = 852)
@Composable
private fun NeedWantFinishedPreview() {
    val initial = PracticeGames.needs()
    NeedWantPreviewContent(PracticeChoiceState(initial.questions, initial.questions.size,
        initial.questions.map { it.correctChoice }))
}

@Composable
private fun NeedWantPreviewContent(initial: PracticeChoiceState<NeedWantQuestion> = PracticeGames.needs()) {
    var game by remember { mutableStateOf(initial) }
    LCTAppTheme {
        NeedWantScreen(game, onAction = { game = game.reduce(it) },
            onBack = { game = PracticeGames.needs() })
    }
}
