package ru.nksk.lctapp.domain.onboarding

import ru.nksk.lctapp.domain.pet.PetCustomization

/** A draft is not a saved game. Its presence resumes customization before completion. */
enum class OnboardingStep { Profile, Accessories, GoalBriefing, GoalSelection, Introduction }

data class OnboardingDraft(
    val profile: PetCustomization,
    val step: OnboardingStep = OnboardingStep.Profile,
    val accessoryId: String = "BACKPACK",
    val goalId: String? = null,
) {
    val hasValidChoices: Boolean get() = profile.name.isNotBlank() &&
        accessoryId in setOf("PLAIN", "BACKPACK", "BANDANA")
    val canFinish: Boolean get() = step == OnboardingStep.Introduction && hasValidChoices && !goalId.isNullOrBlank()
}

interface OnboardingDraftRepository {
    suspend fun read(): OnboardingDraft?
    suspend fun save(draft: OnboardingDraft)
    suspend fun clear()
}
