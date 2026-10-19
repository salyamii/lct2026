package ru.nksk.lctapp.feature.storygames.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingComparison
import ru.nksk.lctapp.domain.minigame.chapterone.ShoppingSide
import ru.nksk.lctapp.domain.minigame.chapterone.TripPacking
import ru.nksk.lctapp.domain.minigame.chapterone.TripSupply

sealed interface TripPackingAction {
    data class Toggle(val supply: TripSupply) : TripPackingAction
    data object Finish : TripPackingAction
}

@HiltViewModel
class TripPackingGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(TripPacking.restore(
        savedState.get<ArrayList<String>>("trip_supplies")?.toList().orEmpty(),
        savedState["trip_confirmed"] ?: false,
    ))
    val uiState = mutableState.asStateFlow()

    fun onAction(action: TripPackingAction) {
        val current = mutableState.value
        val next = when (action) {
            is TripPackingAction.Toggle -> current.toggle(action.supply)
            TripPackingAction.Finish -> current.finish()
        }
        if (next == current) return
        savedState["trip_supplies"] = ArrayList(next.selected.map { it.name })
        savedState["trip_confirmed"] = next.confirmed
        mutableState.value = next
    }
}

sealed interface ShoppingComparisonAction {
    data class Choose(val side: ShoppingSide) : ShoppingComparisonAction
    data object Next : ShoppingComparisonAction
}

@HiltViewModel
class ShoppingComparisonGameViewModel @Inject constructor(private val savedState: SavedStateHandle) : ViewModel() {
    val seriesId: String = savedState.get<String>("shopping_series_id")?.takeIf { it.isNotBlank() }
        ?: UUID.randomUUID().toString().also { savedState["shopping_series_id"] = it }
    private val mutableState = MutableStateFlow(ShoppingComparison.restore(
        savedState.get<ArrayList<String>>("shopping_answers")?.toList().orEmpty(),
        savedState["shopping_feedback"] ?: false,
    ))
    val uiState = mutableState.asStateFlow()

    fun onAction(action: ShoppingComparisonAction) {
        val current = mutableState.value
        val next = when (action) {
            is ShoppingComparisonAction.Choose -> current.choose(action.side)
            ShoppingComparisonAction.Next -> current.next()
        }
        if (next == current) return
        savedState["shopping_answers"] = ArrayList(next.answers.map { it.selected.name })
        savedState["shopping_feedback"] = next.feedbackShown
        mutableState.value = next
    }
}
