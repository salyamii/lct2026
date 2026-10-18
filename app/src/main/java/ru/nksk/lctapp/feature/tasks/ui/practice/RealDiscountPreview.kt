package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.minigame.DiscountQuestion
import ru.nksk.lctapp.domain.minigame.PracticeChoiceAction
import ru.nksk.lctapp.domain.minigame.PracticeChoiceState
import ru.nksk.lctapp.domain.minigame.PracticeGames

// Android Studio → Split/Design → Start Interactive Mode.
// The real screen and domain reducer run locally, with no Hilt, navigation or game save.
@Preview(name = "Настоящая скидка · играть", group = "Настоящая скидка", widthDp = 393, heightDp = 852)
@Composable
private fun RealDiscountInteractivePreview() = RealDiscountPreviewContent()

@Preview(name = "Верный ответ", group = "Настоящая скидка", widthDp = 393, heightDp = 852)
@Composable
private fun RealDiscountCorrectPreview() {
    val initial = PracticeGames.discounts()
    RealDiscountPreviewContent(initial.reduce(PracticeChoiceAction.Answer(initial.question.correctChoice, 0)))
}

@Preview(name = "Ошибка · 320 dp", group = "Настоящая скидка", widthDp = 320, heightDp = 640)
@Preview(name = "Ошибка · крупный шрифт", group = "Настоящая скидка", widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun RealDiscountErrorPreview() {
    val initial = PracticeGames.discounts()
    RealDiscountPreviewContent(initial.reduce(PracticeChoiceAction.Answer(
        (initial.question.correctChoice + 1) % initial.question.choiceCount, 0)))
}

@Preview(name = "Итог · повтор", group = "Настоящая скидка", widthDp = 393, heightDp = 852)
@Composable
private fun RealDiscountFinishedPreview() {
    val initial = PracticeGames.discounts()
    RealDiscountPreviewContent(PracticeChoiceState(initial.questions, initial.questions.size,
        initial.questions.map { it.correctChoice }))
}

@Composable
private fun RealDiscountPreviewContent(initial: PracticeChoiceState<DiscountQuestion> = PracticeGames.discounts()) {
    var game by remember { mutableStateOf(initial) }
    LCTAppTheme {
        RealDiscountScreen(game, onAction = { game = game.reduce(it) },
            onBack = { game = PracticeGames.discounts() })
    }
}
