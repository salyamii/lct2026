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
            when (it.step) { "PROFILE" -> OnboardingStep.Profile; "ACCESSORIES" -> OnboardingStep.Accessories; else -> error("Unknown onboarding step: ${it.step}") }, it.accessoryId)
    }
    override suspend fun save(draft: OnboardingDraft) {
        database.withWriteTransaction {
            // A late editor callback must not recreate a draft after the game has been committed.
            if (database.gameStateDao().readStates().isEmpty()) {
                database.onboardingDraftDao().save(OnboardingDraftEntity(
                    name = draft.profile.name, temperament = StoredCodes.petTemperament.encode(draft.profile.temperament), fur = StoredCodes.petFur.encode(draft.profile.fur),
                    step = when (draft.step) { OnboardingStep.Profile -> "PROFILE"; OnboardingStep.Accessories -> "ACCESSORIES" },
                    accessoryId = draft.accessoryId))
            }
        }
    }
    override suspend fun clear() = database.onboardingDraftDao().clear()
}
