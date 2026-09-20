package ru.nksk.lctapp.data.game.local

import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.EconomyState
import ru.nksk.lctapp.domain.location.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetState
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
    petTemperament = pet.temperament?.let(StoredCodes.petTemperament::encode),
    petName = pet.name,
    petAge = StoredCodes.petAge.encode(pet.age),
    petColor = StoredCodes.petColor.encode(pet.color),
    locationId = locationScene.location.code,
    locationLighting = StoredCodes.locationLighting.encode(locationScene.lighting),
)

internal fun GameStateEntity.toDomain(
    decisions: List<PlayerDecisionEntity>,
    items: List<OwnedItemEntity>,
) = GameState(
    pet = PetState(selectedLook, StoredCodes.visual.decode(visualState), petName,
        StoredCodes.petAge.decode(petAge), StoredCodes.petColor.decode(petColor), petTemperament?.let(StoredCodes.petTemperament::decode)),
    economy = EconomyState(balance, BudgetPlan(plannedNeeds, plannedWants, plannedSavings, plannedReserve)),
    story = StoryState(currentDayId, nextScriptPosition, activeEventId, decisions.map { StoryDecision(it.id, it.choiceId) }),
    satiety = satiety,
    fatigue = fatigue,
    ownedItems = items.map { OwnedItem(it.id, it.itemId) },
    locationScene = LocationScene(GameLocation.fromCode(locationId), StoredCodes.locationLighting.decode(locationLighting)),
)
