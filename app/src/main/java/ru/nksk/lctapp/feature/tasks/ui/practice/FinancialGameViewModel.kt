package ru.nksk.lctapp.feature.tasks.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nksk.lctapp.domain.minigame.*

/** Temporary board only. The ordinary deed host owns pause, payment and retries. */
@HiltViewModel
internal class FinancialGameViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FinancialBoard?>(null)
    val uiState = mutableState.asStateFlow()
    private var owner: String? = null

    fun bind(occurrenceId: String, kind: DeedGameKind) {
        val key = "$occurrenceId:${kind.name}"
        if (owner == key) return
        owner = key
        val restored = if (savedState.get<String>(OWNER) == key) {
            savedState.get<String>(BOARD)?.let { encoded ->
                runCatching { Json.decodeFromString<FinancialBoard>(encoded) }.getOrNull()
            }
        } else null
        savedState[OWNER] = key
        update(restored?.takeIf { it.kind == kind && it.matchesExercises() } ?: FinancialBoard.create(kind))
    }

    fun onChoice(action: PracticeChoiceAction) {
        // Replay belongs to previews. Paid work can only settle once through its host.
        if (action == PracticeChoiceAction.Restart) return
        val board = mutableState.value ?: return
        update(when (board) {
            is FinancialBoard.Needs -> board.copy(game = board.game.reduce(action))
            is FinancialBoard.Discounts -> board.copy(game = board.game.reduce(action))
            is FinancialBoard.Traps -> board.copy(game = board.game.reduce(action))
            is FinancialBoard.Extra -> board.copy(game = board.game.reduce(action))
            is FinancialBoard.Balance -> return
        })
    }

    fun onBalance(action: CoinBalanceAction) {
        if (action == CoinBalanceAction.Restart) return
        val board = mutableState.value as? FinancialBoard.Balance ?: return
        update(board.copy(game = board.game.reduce(action)))
    }

    private fun update(board: FinancialBoard) {
        savedState[BOARD] = Json.encodeToString(board)
        mutableState.value = board
    }

    private companion object {
        const val OWNER = "financial_game_owner_v1"
        const val BOARD = "financial_game_board_v1"
    }
}

@Serializable
internal sealed interface FinancialBoard {
    val kind: DeedGameKind

    @Serializable data class Needs(val game: PracticeChoiceState<NeedWantQuestion> = PracticeGames.needs()) : FinancialBoard {
        override val kind get() = DeedGameKind.NEED_WANT
    }
    @Serializable data class Discounts(val game: PracticeChoiceState<DiscountQuestion> = PracticeGames.discounts()) : FinancialBoard {
        override val kind get() = DeedGameKind.REAL_DISCOUNT
    }
    @Serializable data class Balance(val game: CoinBalanceState = CoinBalanceState.create()) : FinancialBoard {
        override val kind get() = DeedGameKind.COIN_BALANCE
    }
    @Serializable data class Traps(val game: PracticeChoiceState<FinancialTrapQuestion> = PracticeGames.traps()) : FinancialBoard {
        override val kind get() = DeedGameKind.FINANCIAL_TRAP
    }
    @Serializable data class Extra(val game: PracticeChoiceState<ExtraPurchaseQuestion> = PracticeGames.extraPurchases()) : FinancialBoard {
        override val kind get() = DeedGameKind.EXTRA_PURCHASE
    }

    fun score(): DeedGameScore? = when (this) {
        is Needs -> DeedGameScore.fromNeedWant(game)
        is Discounts -> DeedGameScore.fromRealDiscount(game)
        is Balance -> DeedGameScore.fromCoinBalance(game)
        is Traps -> DeedGameScore.fromFinancialTrap(game)
        is Extra -> DeedGameScore.fromExtraPurchase(game)
    }

    fun matchesExercises(): Boolean = when (this) {
        is Needs -> game.questions == PracticeGames.needs().questions
        is Discounts -> game.questions == PracticeGames.discounts().questions
        is Balance -> game.rounds == CoinBalanceState.create().rounds
        is Traps -> game.questions == PracticeGames.traps().questions
        is Extra -> game.questions == PracticeGames.extraPurchases().questions
    }

    companion object {
        fun create(kind: DeedGameKind): FinancialBoard = when (kind) {
            DeedGameKind.NEED_WANT -> Needs()
            DeedGameKind.REAL_DISCOUNT -> Discounts()
            DeedGameKind.COIN_BALANCE -> Balance()
            DeedGameKind.FINANCIAL_TRAP -> Traps()
            DeedGameKind.EXTRA_PURCHASE -> Extra()
            else -> error("Not a financial exercise: $kind")
        }
    }
}
