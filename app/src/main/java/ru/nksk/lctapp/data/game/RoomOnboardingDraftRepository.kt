package ru.nksk.lctapp.data.game

import androidx.room3.withWriteTransaction
import javax.inject.Inject
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.OnboardingDraftEntity
import ru.nksk.lctapp.domain.onboarding.*
import ru.nksk.lctapp.domain.pet.PetCustomization
import ru.nksk.lctapp.data.game.local.StoredCodes

internal class RoomOnboardingDraftRepository @Inject constructor(private val database: GameDatabase) : OnboardingDraftRepository {
    override suspend fun read() = database.onboardingDraftDao().read()?.let {
        OnboardingDraft(PetCustomization(it.name, StoredCodes.petTemperament.decode(it.temperament), StoredCodes.petFur.decode(it.fur)),
            when (it.step) { "CHARACTER" -> OnboardingStep.Character; "PROFILE" -> OnboardingStep.Profile; "ACCESSORIES" -> OnboardingStep.Accessories; "GOAL_BRIEFING" -> OnboardingStep.GoalBriefing; "GOAL_SELECTION" -> OnboardingStep.GoalSelection; "INTRODUCTION" -> OnboardingStep.Introduction; else -> error("Unknown onboarding step: ${it.step}") }, it.accessoryId, it.savingItemId)
    }
    override suspend fun save(draft: OnboardingDraft) {
        database.withWriteTransaction {
            // A late editor callback must not recreate a draft after the game has been committed.
            if (database.gameStateDao().readStates().isEmpty()) {
                database.onboardingDraftDao().save(OnboardingDraftEntity(
                    name = draft.profile.name, temperament = StoredCodes.petTemperament.encode(draft.profile.temperament), fur = StoredCodes.petFur.encode(draft.profile.fur),
                    step = when (draft.step) { OnboardingStep.Character -> "CHARACTER"; OnboardingStep.Profile -> "PROFILE"; OnboardingStep.Accessories -> "ACCESSORIES"; OnboardingStep.GoalBriefing -> "GOAL_BRIEFING"; OnboardingStep.GoalSelection -> "GOAL_SELECTION"; OnboardingStep.Introduction -> "INTRODUCTION" },
                    accessoryId = draft.accessoryId, savingItemId = draft.savingItemId))
            }
        }
    }
    override suspend fun clear() = database.onboardingDraftDao().clear()
}
