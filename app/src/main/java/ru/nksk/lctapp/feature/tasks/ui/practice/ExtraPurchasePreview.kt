package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.minigame.ExtraPurchaseQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.domain.minigame.PracticeGames

// Android Studio → Split/Design → Start Interactive Mode.
// The real screen and domain reducer run locally, with no Hilt, navigation or game save.
@Preview(name = "Лишняя покупка · играть", group = "Лишняя покупка", widthDp = 393, heightDp = 852)
@Composable
private fun ExtraPurchaseInteractivePreview() = ExtraPurchasePreviewContent()

@Preview(name = "Верный ответ", group = "Лишняя покупка", widthDp = 393, heightDp = 852)
@Composable
private fun ExtraPurchaseCorrectPreview() {
    val initial = PracticeGames.extraPurchases()
    ExtraPurchasePreviewContent(initial.reduce(PracticeChoiceAction.Answer(initial.question.correctChoice, 0)))
}

@Preview(name = "Ошибка · 320 dp", group = "Лишняя покупка", widthDp = 320, heightDp = 640)
@Preview(name = "Ошибка · крупный шрифт", group = "Лишняя покупка", widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun ExtraPurchaseErrorPreview() {
    val initial = PracticeGames.extraPurchases()
    ExtraPurchasePreviewContent(initial.reduce(PracticeChoiceAction.Answer(
        (initial.question.correctChoice + 1) % initial.question.choiceCount, 0)))
}

@Preview(name = "Итог · повтор", group = "Лишняя покупка", widthDp = 393, heightDp = 852)
@Composable
private fun ExtraPurchaseFinishedPreview() {
    val initial = PracticeGames.extraPurchases()
    ExtraPurchasePreviewContent(PracticeChoiceState(initial.questions, initial.questions.size,
        initial.questions.map { it.correctChoice }))
}

@Composable
private fun ExtraPurchasePreviewContent(initial: PracticeChoiceState<ExtraPurchaseQuestion> = PracticeGames.extraPurchases()) {
    var game by remember { mutableStateOf(initial) }
    LCTAppTheme {
        ExtraPurchaseScreen(game, onAction = { game = game.reduce(it) },
            onBack = { game = PracticeGames.extraPurchases() })
    }
}
