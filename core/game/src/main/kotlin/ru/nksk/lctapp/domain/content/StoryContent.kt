package ru.nksk.lctapp.domain.content

/** Offline reference catalog. Root lists are unordered; authored positions order child lists. */
data class StoryContent(
    val chapters: List<ChapterDefinition> = emptyList(),
    val days: List<GameDayDefinition> = emptyList(),
    val schedule: List<ScheduledEvent> = emptyList(),
    val events: List<EventDefinition> = emptyList(),
    val choices: List<EventChoiceDefinition> = emptyList(),
    val items: List<ItemDefinition> = emptyList(),
    val goals: List<GoalDefinition> = emptyList(),
    val requiredItems: List<GoalRequiredItem> = emptyList(),
    val eventItemEffects: List<EventItemEffect> = emptyList(),
    val choiceItemEffects: List<ChoiceItemEffect> = emptyList(),
)

data class ChapterDefinition(val id: String, val title: String, val goalId: String)
data class GameDayDefinition(val id: String, val chapterId: String, val dayNumber: Int)
data class ScheduledEvent(val id: String, val dayId: String, val position: Int, val eventId: String)
/** Catalog price, not a purchase receipt. Null preserves older content with no authored price. */
data class ItemDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: ItemCategory = ItemCategory.STORY,
    val priceCoins: Long? = null,
)

enum class ItemCategory { STORY, ACCESSORY }
data class GoalDefinition(val id: String, val title: String, val description: String)
data class GoalRequiredItem(val goalId: String, val itemId: String)
