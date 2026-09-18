package ru.nksk.lctapp.data.game.local

import ru.nksk.lctapp.domain.content.GoalDefinition
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.content.ChapterDefinition
import ru.nksk.lctapp.domain.content.GameDayDefinition
import ru.nksk.lctapp.domain.content.ScheduledEvent
import ru.nksk.lctapp.domain.content.GoalRequiredItem
import ru.nksk.lctapp.domain.content.EventDefinition
import ru.nksk.lctapp.domain.content.EventChoiceDefinition
import ru.nksk.lctapp.domain.content.EventItemEffect
import ru.nksk.lctapp.domain.content.ChoiceItemEffect

internal fun GoalDefinition.toEntity() = GoalEntity(
    id = id,
    title = title,
    description = description,
)

internal fun GoalEntity.toDomain() = GoalDefinition(
    id = id,
    title = title,
    description = description,
)

internal fun ItemDefinition.toEntity() = ItemEntity(
    id = id,
    name = name,
    description = description,
)

internal fun ItemEntity.toDomain() = ItemDefinition(
    id = id,
    name = name,
    description = description,
)

internal fun ChapterDefinition.toEntity() = ChapterEntity(
    id = id,
    title = title,
    goalId = goalId,
)

internal fun ChapterEntity.toDomain() = ChapterDefinition(
    id = id,
    title = title,
    goalId = goalId,
)

internal fun GameDayDefinition.toEntity() = GameDayEntity(
    id = id,
    chapterId = chapterId,
    dayNumber = dayNumber,
)

internal fun GameDayEntity.toDomain() = GameDayDefinition(
    id = id,
    chapterId = chapterId,
    dayNumber = dayNumber,
)

internal fun ScheduledEvent.toEntity() = DayEventEntity(
    id = id,
    dayId = dayId,
    position = position,
    eventId = eventId,
)

internal fun DayEventEntity.toDomain() = ScheduledEvent(
    id = id,
    dayId = dayId,
    position = position,
    eventId = eventId,
)

internal fun GoalRequiredItem.toEntity() = GoalRequiredItemEntity(
    goalId = goalId,
    itemId = itemId,
)

internal fun GoalRequiredItemEntity.toDomain() = GoalRequiredItem(
    goalId = goalId,
    itemId = itemId,
)

internal fun EventDefinition.toEntity() = EventEntity(
    id = id,
    type = StoredCodes.event.encode(type),
    title = title,
    description = description,
    minSatiety = minSatiety,
    maxFatigue = maxFatigue,
    petStateOnStart = petStateOnStart?.let(StoredCodes.visual::encode),
    moneyDeltaOnStart = moneyDeltaOnStart,
    budgetSectionOnStart = budgetSectionOnStart?.let(StoredCodes.budget::encode),
    nextChapterId = nextChapterId,
)

internal fun EventEntity.toDomain() = EventDefinition(
    id = id,
    type = StoredCodes.event.decode(type),
    title = title,
    description = description,
    minSatiety = minSatiety,
    maxFatigue = maxFatigue,
    petStateOnStart = petStateOnStart?.let(StoredCodes.visual::decode),
    moneyDeltaOnStart = moneyDeltaOnStart,
    budgetSectionOnStart = budgetSectionOnStart?.let(StoredCodes.budget::decode),
    nextChapterId = nextChapterId,
)

internal fun EventChoiceDefinition.toEntity() = EventChoiceEntity(
    id = id,
    eventId = eventId,
    position = position,
    text = text,
    moneyDelta = moneyDelta,
    budgetSection = budgetSection?.let(StoredCodes.budget::encode),
    petStateAfter = petStateAfter?.let(StoredCodes.visual::encode),
    goalImpact = StoredCodes.impact.encode(goalImpact),
)

internal fun EventChoiceEntity.toDomain() = EventChoiceDefinition(
    id = id,
    eventId = eventId,
    position = position,
    text = text,
    moneyDelta = moneyDelta,
    budgetSection = budgetSection?.let(StoredCodes.budget::decode),
    petStateAfter = petStateAfter?.let(StoredCodes.visual::decode),
    goalImpact = StoredCodes.impact.decode(goalImpact),
)

internal fun EventItemEffect.toEntity() = EventItemEffectEntity(
    id = id,
    eventId = eventId,
    position = position,
    itemId = itemId,
    operation = StoredCodes.operation.encode(operation),
)

internal fun EventItemEffectEntity.toDomain() = EventItemEffect(
    id = id,
    eventId = eventId,
    position = position,
    itemId = itemId,
    operation = StoredCodes.operation.decode(operation),
)

internal fun ChoiceItemEffect.toEntity() = ChoiceItemEffectEntity(
    id = id,
    choiceId = choiceId,
    position = position,
    itemId = itemId,
    operation = StoredCodes.operation.encode(operation),
)

internal fun ChoiceItemEffectEntity.toDomain() = ChoiceItemEffect(
    id = id,
    choiceId = choiceId,
    position = position,
    itemId = itemId,
    operation = StoredCodes.operation.decode(operation),
)
