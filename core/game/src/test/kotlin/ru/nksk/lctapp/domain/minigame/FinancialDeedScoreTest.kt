package ru.nksk.lctapp.domain.minigame

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class FinancialDeedScoreTest {
    @Test fun completeOriginalExercisesProduceTheirOwnScores() {
        assertNull(DeedGameScore.fromNeedWant(PracticeGames.needs()))
        assertNull(DeedGameScore.fromRealDiscount(PracticeGames.discounts()))
        assertNull(DeedGameScore.fromFinancialTrap(PracticeGames.traps()))
        assertNull(DeedGameScore.fromExtraPurchase(PracticeGames.extraPurchases()))
        val scores = listOf(
            DeedGameScore.fromNeedWant(finish(PracticeGames.needs())),
            DeedGameScore.fromRealDiscount(finish(PracticeGames.discounts())),
            DeedGameScore.fromFinancialTrap(finish(PracticeGames.traps())),
            DeedGameScore.fromExtraPurchase(finish(PracticeGames.extraPurchases())),
        ).map(::checkNotNull)
        assertEquals(listOf(8, 5, 4, 3), scores.map { it.attempts })
        scores.forEach { score ->
            assertEquals(5L, score.reward(5))
            val restored = Json.decodeFromString<DeedGameScore>(Json.encodeToString(score))
            assertEquals(score.kind, restored.kind)
            assertEquals(score.reward(5), restored.reward(5))
        }
        val allWrong = finish(PracticeGames.needs(), wrong = true)
        assertEquals(1L, checkNotNull(DeedGameScore.fromNeedWant(allWrong)).reward(5))
        assertEquals(1L, DeedRewardPreview.fromPractice(allWrong).reward(5))
        assertEquals(0L, checkNotNull(DeedGameScore.fromNeedWant(allWrong)).reward(0))
    }

    @Test fun shortenedOrChangedExercisesCannotSettleAnOriginalDeed() {
        val needs = PracticeGames.needs()
        assertNull(DeedGameScore.fromNeedWant(finish(PracticeChoiceState(needs.questions.take(1)))))
        assertNull(DeedGameScore.fromNeedWant(finish(PracticeChoiceState(needs.questions.reversed()))))
        val answered = needs.copy(current = 7, answers = needs.questions.map { it.correctChoice })
        assertNull(DeedGameScore.fromNeedWant(answered))
        assertNotNull(DeedGameScore.fromNeedWant(answered.reduce(PracticeChoiceAction.Next(7))))
    }

    @Test fun balanceMistakeLowersForecastOnceAndCorrectionStillAllowsCompletion() {
        var game = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
        assertNull(DeedGameScore.fromCoinBalance(game))
        game = game.reduce(CoinBalanceAction.Check(0))
        assertEquals(3L, DeedRewardPreview.fromCoinBalance(game).reward(5))
        game = game.reduce(CoinBalanceAction.Edit(0)).reduce(CoinBalanceAction.Check(0))
        assertEquals(3L, DeedRewardPreview.fromCoinBalance(game).reward(5))
        game = game.reduce(CoinBalanceAction.Edit(0))
        repeat(3) { round ->
            game.round.expenses.filter { !it.required }.forEach {
                game = game.reduce(CoinBalanceAction.Toggle(it.item, round))
            }
            game = game.reduce(CoinBalanceAction.Check(round))
            assertNull(DeedGameScore.fromCoinBalance(game))
            game = game.reduce(CoinBalanceAction.Next(round))
        }
        val score = checkNotNull(DeedGameScore.fromCoinBalance(game))
        assertEquals(2, score.correct)
        assertEquals(3L, score.reward(5))
        assertEquals(score.reward(5), DeedRewardPreview.fromCoinBalance(game).reward(5))
        assertEquals(game, Json.decodeFromString<CoinBalanceState>(Json.encodeToString(game)))
    }

    private fun <Q : PracticeQuestion> finish(initial: PracticeChoiceState<Q>, wrong: Boolean = false): PracticeChoiceState<Q> {
        var game = initial
        while (!game.finished) {
            val answer = if (wrong) (game.question.correctChoice + 1) % game.question.choiceCount else game.question.correctChoice
            game = game.reduce(PracticeChoiceAction.Answer(answer, game.current))
                .reduce(PracticeChoiceAction.Next(game.current))
        }
        return game
    }
}
