package ru.nksk.lctapp.domain.pet

/** Logical appearance. Android artwork selection belongs to presentation code. */
sealed interface PetAppearance {
    data class SelectedLook(val look: PetLook) : PetAppearance

    /** A special image replaces the entire cosmetic appearance, including accessories. */
    data class SpecialState(val state: PetVisualState) : PetAppearance {
        init {
            require(state != PetVisualState.NORMAL) {
                "NORMAL must display the selected look"
            }
        }
    }
}
