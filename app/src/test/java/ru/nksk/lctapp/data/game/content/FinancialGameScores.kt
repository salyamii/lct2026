package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.minigame.*

/** Campaign simulations retain their accuracy cap when the scheduler chooses a financial deed. */
internal fun completedFinancialGameScore(kind: DeedGameKind, cap: Int = 100): DeedGameScore = checkNotNull(when (kind) {
    DeedGameKind.NEED_WANT -> DeedGameScore.fromNeedWant(answerSeries(PracticeGames.needs(), cap))
    DeedGameKind.REAL_DISCOUNT -> DeedGameScore.fromRealDiscount(answerSeries(PracticeGames.discounts(), cap))
    DeedGameKind.FINANCIAL_TRAP -> DeedGameScore.fromFinancialTrap(answerSeries(PracticeGames.traps(), cap))
    DeedGameKind.EXTRA_PURCHASE -> DeedGameScore.fromExtraPurchase(answerSeries(PracticeGames.extraPurchases(), cap))
    DeedGameKind.COIN_BALANCE -> {
        var game = CoinBalanceState.create().reduce(CoinBalanceAction.Start)
        val correctRounds = game.rounds.size * cap / 100
        while (!game.finished) {
            if (game.current >= correctRounds) {
                game = game.reduce(CoinBalanceAction.Check(game.current)).reduce(CoinBalanceAction.Edit(game.current))
            }
            game.round.expenses.filter { !it.required }.forEach {
                game = game.reduce(CoinBalanceAction.Toggle(it.item, game.current))
            }
            game = game.reduce(CoinBalanceAction.Check(game.current)).reduce(CoinBalanceAction.Next(game.current))
        }
        DeedGameScore.fromCoinBalance(game)
    }
    else -> error("Not a financial game: $kind")
})

private fun <Q : PracticeQuestion> answerSeries(initial: PracticeChoiceState<Q>, cap: Int): PracticeChoiceState<Q> {
    require(cap in 0..100)
    val correctAnswers = initial.questions.size * cap / 100
    var game = initial
    while (!game.finished) {
        val choice = if (game.current < correctAnswers) game.question.correctChoice
            else (game.question.correctChoice + 1) % game.question.choiceCount
        game = game.reduce(PracticeChoiceAction.Answer(choice, game.current))
            .reduce(PracticeChoiceAction.Next(game.current))
    }
    return game
}
