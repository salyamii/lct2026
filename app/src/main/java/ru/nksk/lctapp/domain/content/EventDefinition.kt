package ru.nksk.lctapp.domain.content

import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.pet.PetVisualState

enum class EventType { STATE, RANDOM, WANT, EARNING, STORY }
enum class GoalImpact { BAD, GOOD, NEUTRAL }
enum class ItemOperation { ADD, REMOVE }

/** Authored data only. Storage never applies these effects or invents event lifecycle rules. */
data class EventDefinition(
    val id: String,
    val type: EventType,
    val title: String,
    val description: String,
    val minSatiety: Int?,
    val maxFatigue: Int?,
    val petStateOnStart: PetVisualState?,
    val moneyDeltaOnStart: Long,
    val budgetSectionOnStart: BudgetSection?,
    val nextChapterId: String?,
)

data class EventChoiceDefinition(
    val id: String,
    val eventId: String,
    val position: Int,
    val text: String,
    val moneyDelta: Long,
    val budgetSection: BudgetSection?,
    val petStateAfter: PetVisualState?,
    val goalImpact: GoalImpact,
)

data class EventItemEffect(
    val id: String, val eventId: String, val position: Int, val itemId: String, val operation: ItemOperation,
)

data class ChoiceItemEffect(
    val id: String, val choiceId: String, val position: Int, val itemId: String, val operation: ItemOperation,
)
