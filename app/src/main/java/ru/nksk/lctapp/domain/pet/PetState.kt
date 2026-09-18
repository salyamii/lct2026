package ru.nksk.lctapp.domain.pet

/** One current state and a separate saved look. No timers, hidden states, or queues. */
data class PetState(
    val selectedLook: PetLook,
    val visualState: PetVisualState,
) {
    val appearance: PetAppearance
        get() = if (visualState == PetVisualState.NORMAL) {
            PetAppearance.SelectedLook(selectedLook)
        } else {
            PetAppearance.SpecialState(visualState)
        }

    /**
     * Applies an outcome already decided by game logic, preserving the selected look.
     * Event handlers own eligibility and consequences; this method does not invent them.
     */
    fun transitionTo(nextState: PetVisualState): PetState = copy(visualState = nextState)
}
