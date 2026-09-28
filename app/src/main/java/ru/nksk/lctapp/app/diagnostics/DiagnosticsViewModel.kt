package ru.nksk.lctapp.app.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.nksk.lctapp.data.diagnostics.AppDiagnostics
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.game.GameState

/** Keeps a small, anonymous context ready before a failure; never loads the audit journal. */
@HiltViewModel
internal class DiagnosticsViewModel @Inject constructor(
    private val diagnostics: AppDiagnostics,
    private val session: GameSession,
) : ViewModel() {
    init {
        diagnostics.updateContext("last_committed_action", "none")
        viewModelScope.launch {
            session.appliedCommands.collect { applied ->
                diagnostics.updateContext("last_committed_action", applied.request.command.diagnosticAction())
            }
        }
        viewModelScope.launch {
            session.observe()
                .map(::project)
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                // A failed diagnostic read must not turn an ordinary storage error into a crash.
                .catch { error ->
                    if (error !is Exception) throw error
                    emit(GameContext("unavailable", "unavailable", "unavailable", "unavailable"))
                }
                .collect { context ->
                    diagnostics.updateContext("chapter", context.chapter)
                    diagnostics.updateContext("age", context.age)
                    diagnostics.updateContext("accessory", context.accessory)
                    diagnostics.updateContext("phase", context.phase)
                }
        }
    }

    fun screenShown(screen: String) = diagnostics.updateContext("screen", screen)

    private fun project(game: GameState?): GameContext {
        if (game == null) return GameContext("none", "none", "none", "none")
        val acts = session.catalog.storyCampaign?.acts.orEmpty()
        val currentAct = session.catalog.storyProgress(game).currentAct
        val chapter = when {
            acts.isEmpty() -> "none"
            currentAct == null -> acts.size.toString()
            else -> (acts.indexOf(currentAct) + 1).toString()
        }
        // selectedLookId is an open content key. Only known, fixed categories may enter the log.
        val accessory = when (game.pet.selectedLookId) {
            "PLAIN" -> "plain"
            "BANDANA" -> "bandana"
            "BACKPACK" -> "backpack"
            "HAT" -> "hat"
            "GLASSES" -> "glasses"
            "ROUTE_PATCH" -> "route_patch"
            "COMPASS" -> "compass"
            "BINOCULARS" -> "binoculars"
            else -> "other"
        }
        return GameContext(chapter, game.pet.age.name, accessory, game.engine?.phase?.name ?: "none")
    }
}

private data class GameContext(val chapter: String, val age: String, val accessory: String, val phase: String)

/** Fixed labels survive obfuscation and never include command payloads or user-entered text. */
private fun EngineCommand.diagnosticAction(): String = when (this) {
    is EngineCommand.StartBudgetAllocation -> "StartBudgetAllocation"
    is EngineCommand.ChangeBudgetAllocation -> "ChangeBudgetAllocation"
    is EngineCommand.ConfirmBudget -> "ConfirmBudget"
    is EngineCommand.DepositSavings -> "DepositSavings"
    is EngineCommand.WithdrawSavings -> "WithdrawSavings"
    is EngineCommand.RequestFinancialPractice -> "RequestFinancialPractice"
    is EngineCommand.AnswerFinancialQuestion -> "AnswerFinancialQuestion"
    is EngineCommand.AdvanceFinancialPractice -> "AdvanceFinancialPractice"
    EngineCommand.CloseFinancialPractice -> "CloseFinancialPractice"
    is EngineCommand.RenamePet -> "RenamePet"
    is EngineCommand.SetPetColor -> "SetPetColor"
    is EngineCommand.SetPetLook -> "SetPetLook"
    is EngineCommand.BeginDay -> "BeginDay"
    EngineCommand.OpenNextEvent -> "OpenNextEvent"
    is EngineCommand.SelectGoal -> "SelectGoal"
    is EngineCommand.SelectSavingGoal -> "SelectSavingGoal"
    is EngineCommand.BuyGoalItem -> "BuyGoalItem"
    is EngineCommand.Choose -> "Choose"
    is EngineCommand.CompleteEvent -> "CompleteEvent"
    is EngineCommand.AcknowledgeResult -> "AcknowledgeResult"
    is EngineCommand.StartDeed -> "StartDeed"
    is EngineCommand.AcceptDeedProposal -> "AcceptDeedProposal"
    is EngineCommand.CompleteDeed -> "CompleteDeed"
    is EngineCommand.StartStoryGame -> "StartStoryGame"
    is EngineCommand.CompleteStoryGame -> "CompleteStoryGame"
    is EngineCommand.DismissDeedProposal -> "DismissDeedProposal"
    is EngineCommand.PauseEvent -> "PauseEvent"
    is EngineCommand.Feed -> "Feed"
    is EngineCommand.FinishDayFromEvent -> "FinishDayFromEvent"
    EngineCommand.FinishDay -> "FinishDay"
}
