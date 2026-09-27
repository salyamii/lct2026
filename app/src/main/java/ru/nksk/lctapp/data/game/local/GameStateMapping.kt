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
    unallocated = economy.unallocated,
    needs = economy.plan.needs,
    wants = economy.plan.wants,
    savings = economy.plan.savings,
    reserve = economy.plan.reserve,
    availableBalance = economy.availableBalance,
    savingsBalance = economy.savingsBalance,
    budgetModelVersion = 1,
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
    planning: BudgetPlanningEntity? = null,
) = GameState(
    pet = PetState(selectedLook, StoredCodes.visual.decode(visualState), petName,
        StoredCodes.petAge.decode(petAge), StoredCodes.petColor.decode(petColor), petTemperament?.let(StoredCodes.petTemperament::decode)),
    economy = EconomyState(plan = BudgetPlan(needs, wants, savings, reserve), unallocated = unallocated, planning = planning?.toDomain(),
        availableBalance = availableBalance, savingsBalance = savingsBalance),
    story = StoryState(currentDayId, nextScriptPosition, activeEventId, decisions.map { StoryDecision(it.id, it.choiceId) }),
    satiety = satiety,
    fatigue = fatigue,
    ownedItems = items.map { OwnedItem(it.id, it.itemId) },
    locationScene = LocationScene(GameLocation.fromCode(locationId), StoredCodes.locationLighting.decode(locationLighting)),
)
