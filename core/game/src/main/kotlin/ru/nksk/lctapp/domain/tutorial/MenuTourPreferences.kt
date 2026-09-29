package ru.nksk.lctapp.domain.tutorial

/** Device-local guidance, independent of the saved game and its backup. */
interface MenuTourPreferences {
    suspend fun readStep(): Int?
    suspend fun saveStep(step: Int)
}
