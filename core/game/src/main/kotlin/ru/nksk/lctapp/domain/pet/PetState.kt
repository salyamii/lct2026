package ru.nksk.lctapp.domain.pet

/** One current state and a separate saved look. No timers, hidden states, or queues. */
@kotlinx.serialization.Serializable
data class PetState(
    val selectedLookId: String,
    val visualState: PetVisualState,
    val name: String = PetDefaults.FOX_NAME,
    val age: PetAge = PetAge.CUB,
    val color: PetColor = PetColor.COPPER,
    val temperament: PetTemperament? = null,
) {
    init { require(isValidPetName(name)) { "A pet name must be nonblank single-line text" } }
    val appearance: PetAppearance
        get() = if (visualState == PetVisualState.NORMAL) {
            PetAppearance.SelectedLook(selectedLookId)
        } else {
            PetAppearance.SpecialState(visualState)
        }

    /**
     * Applies an outcome already decided by game logic, preserving the selected look.
     * Event handlers own eligibility and consequences; this method does not invent them.
     */
    fun transitionTo(nextState: PetVisualState): PetState = copy(visualState = nextState)
}
