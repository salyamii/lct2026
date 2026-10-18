package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import ru.nksk.lctapp.core.ui.theme.LCTAppTheme
import ru.nksk.lctapp.domain.minigame.CoinBalanceAction
import ru.nksk.lctapp.domain.minigame.CoinBalanceState
import ru.nksk.lctapp.domain.minigame.PracticeItem

// Android Studio → Start Interactive Mode: remove/restore expenses, check, correct and replay all three rounds.
@Preview(name = "Монетный баланс · играть", group = "Монетный баланс", widthDp = 393, heightDp = 852)
@Composable
private fun CoinBalanceInteractivePreview() = CoinBalancePreviewContent()

@Preview(name = "Верный баланс", group = "Монетный баланс", widthDp = 393, heightDp = 852)
@Composable
private fun CoinBalanceCorrectPreview() = CoinBalancePreviewContent(CoinBalanceState.create()
    .reduce(CoinBalanceAction.Start)
    .reduce(CoinBalanceAction.Toggle(PracticeItem.BUN, 0))
    .reduce(CoinBalanceAction.Check(0)))

@Preview(name = "Перерасход · 320 dp", group = "Монетный баланс", widthDp = 320, heightDp = 640)
@Composable
private fun CoinBalanceOverspentPreview() = CoinBalancePreviewContent(CoinBalanceState.create()
    .reduce(CoinBalanceAction.Start).reduce(CoinBalanceAction.Check(0)))

@Preview(name = "Снято обязательное · крупный шрифт", group = "Монетный баланс", widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun CoinBalanceRequiredPreview() = CoinBalancePreviewContent(CoinBalanceState.create()
    .reduce(CoinBalanceAction.Start)
    .reduce(CoinBalanceAction.Toggle(PracticeItem.MEDICINE, 0))
    .reduce(CoinBalanceAction.Check(0)))

@Preview(name = "Итог · повтор", group = "Монетный баланс", widthDp = 393, heightDp = 852)
@Composable
private fun CoinBalanceFinishedPreview() = CoinBalancePreviewContent(CoinBalanceState.create().let {
    it.copy(current = it.rounds.size, correctAnswers = it.rounds.size, started = true)
})

@Composable
private fun CoinBalancePreviewContent(initial: CoinBalanceState = CoinBalanceState.create()) {
    var game by remember { mutableStateOf(initial) }
    LCTAppTheme {
        CoinBalanceScreen(game, onAction = { game = game.reduce(it) },
            onBack = { game = CoinBalanceState.create() })
    }
}
