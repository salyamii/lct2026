package ru.nksk.lctapp.data.game.local

import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.domain.pet.PetCustomization
import ru.nksk.lctapp.domain.story.StoryDecision
import ru.nksk.lctapp.domain.story.StoryState

internal const val CURRENT_GAME_ID = "current"

internal fun GameState.toEntity() = GameStateEntity(
    id = CURRENT_GAME_ID,
    visualState = StoredCodes.visual.encode(pet.visualState),
    selectedLook = pet.selectedLookId,
    satiety = satiety,
    fatigue = fatigue,
    balance = economy.balance,
    plannedNeeds = economy.plan.needs,
    plannedWants = economy.plan.wants,
    plannedSavings = economy.plan.savings,
    plannedReserve = economy.plan.reserve,
    currentDayId = story.currentDayId,
    nextScriptPosition = story.nextScriptPosition,
    activeEventId = story.activeEventId,
    petName = pet.customization?.name,
    petTemperament = pet.customization?.temperament?.let(StoredCodes.petTemperament::encode),
    petFur = pet.customization?.fur?.let(StoredCodes.petFur::encode),
    petAge = pet.customization?.age?.let(StoredCodes.petAge::encode),
)

internal fun GameStateEntity.toDomain(
    decisions: List<PlayerDecisionEntity>,
    items: List<OwnedItemEntity>,
) = GameState(
    pet = PetState(selectedLook, StoredCodes.visual.decode(visualState),
        decodeCustomization(petName, petTemperament, petFur, petAge)),
    economy = EconomyState(balance, BudgetPlan(plannedNeeds, plannedWants, plannedSavings, plannedReserve)),
    story = StoryState(currentDayId, nextScriptPosition, activeEventId, decisions.map { StoryDecision(it.id, it.choiceId) }),
    satiety = satiety,
    fatigue = fatigue,
    ownedItems = items.map { OwnedItem(it.id, it.itemId) },
)

private fun decodeCustomization(name: String?, temperament: String?, fur: String?, age: String?): PetCustomization? {
    if (listOf(name, temperament, fur, age).all { it == null }) return null
    return PetCustomization(checkNotNull(name), StoredCodes.petTemperament.decode(checkNotNull(temperament)),
        StoredCodes.petFur.decode(checkNotNull(fur)), StoredCodes.petAge.decode(checkNotNull(age)))
}
