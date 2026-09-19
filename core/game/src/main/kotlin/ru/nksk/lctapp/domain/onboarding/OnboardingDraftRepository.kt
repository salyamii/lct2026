package ru.nksk.lctapp.domain.onboarding

import ru.nksk.lctapp.domain.pet.PetCustomization

/** A draft is not a saved game. Its presence resumes customization before completion. */
enum class OnboardingStep { Profile, Accessories }

data class OnboardingDraft(
    val profile: PetCustomization,
    val step: OnboardingStep = OnboardingStep.Profile,
    val accessoryId: String = "BACKPACK",
) {
    val canFinish: Boolean get() = profile.name.isNotBlank() &&
        step == OnboardingStep.Accessories && accessoryId in setOf("PLAIN", "BACKPACK", "BANDANA")
}

interface OnboardingDraftRepository {
    suspend fun read(): OnboardingDraft?
    suspend fun save(draft: OnboardingDraft)
    suspend fun clear()
}
