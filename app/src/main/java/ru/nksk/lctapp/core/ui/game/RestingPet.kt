package ru.nksk.lctapp.core.ui.game

import androidx.annotation.DrawableRes
import ru.nksk.lctapp.domain.pet.PetState

/** Sleep is a projection of the finished day; it does not overwrite the saved emotion or look. */
@DrawableRes
internal fun restingPetArtwork(pet: PetState): Int = petArtwork(pet.age, pet.color).sleep
