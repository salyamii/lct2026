package ru.nksk.lctapp.domain.engine

import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.PetAge

/** A narrow compatibility patch, not an ordinary goal-selection command or financial revision. */
data class CampaignReconciliation(
    val petAge: PetAge,
    val selectedGoalId: String?,
    val selectedSavingItemId: String?,
    val rebindCurrentPeriod: Boolean = false,
) {
    fun applyTo(current: GameState): GameState {
        val period = current.financial.currentPeriod
        val financial = if (rebindCurrentPeriod && period != null && selectedGoalId != null &&
            period.goalId != selectedGoalId) {
            current.financial.copy(periods = current.financial.periods.map {
                if (it.id == period.id) it.copy(goalId = selectedGoalId, imported = true) else it
            })
        } else current.financial
        val next = current.copy(pet = current.pet.copy(age = petAge), selectedGoalId = selectedGoalId,
            selectedSavingItemId = selectedSavingItemId, financial = financial)
        return if (next == current) current else next.copy(engine = next.engine?.copy(
            revision = Math.addExact(next.engine.revision, 1L),
        ))
    }
}
