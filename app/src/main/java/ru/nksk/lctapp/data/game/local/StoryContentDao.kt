package ru.nksk.lctapp.data.game.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

/** Internal storage primitives. Reference imports go through the transactional repository. */
@Dao
internal interface StoryContentDao {
    @Query("SELECT * FROM GOAL ORDER BY id")
    suspend fun readGoal(): List<GoalEntity>

    @Insert
    suspend fun insertGoal(rows: List<GoalEntity>)

    @Query("SELECT * FROM ITEM ORDER BY id")
    suspend fun readItem(): List<ItemEntity>

    @Insert
    suspend fun insertItem(rows: List<ItemEntity>)

    @Query("SELECT * FROM CHAPTER ORDER BY id")
    suspend fun readChapter(): List<ChapterEntity>

    @Insert
    suspend fun insertChapter(rows: List<ChapterEntity>)

    @Query("SELECT * FROM GAME_DAY ORDER BY id")
    suspend fun readGameDay(): List<GameDayEntity>

    @Insert
    suspend fun insertGameDay(rows: List<GameDayEntity>)

    @Query("SELECT * FROM EVENT ORDER BY id")
    suspend fun readEvent(): List<EventEntity>

    @Insert
    suspend fun insertEvent(rows: List<EventEntity>)

    @Query("SELECT * FROM DAY_EVENT ORDER BY day_id, position")
    suspend fun readDayEvent(): List<DayEventEntity>

    @Insert
    suspend fun insertDayEvent(rows: List<DayEventEntity>)

    @Query("SELECT * FROM EVENT_CHOICE ORDER BY event_id, position")
    suspend fun readEventChoice(): List<EventChoiceEntity>

    @Insert
    suspend fun insertEventChoice(rows: List<EventChoiceEntity>)

    @Query("SELECT * FROM GOAL_REQUIRED_ITEM ORDER BY goal_id, item_id")
    suspend fun readGoalRequiredItem(): List<GoalRequiredItemEntity>

    @Insert
    suspend fun insertGoalRequiredItem(rows: List<GoalRequiredItemEntity>)

    @Query("SELECT * FROM EVENT_ITEM_EFFECT ORDER BY event_id, position")
    suspend fun readEventItemEffect(): List<EventItemEffectEntity>

    @Insert
    suspend fun insertEventItemEffect(rows: List<EventItemEffectEntity>)

    @Query("SELECT * FROM CHOICE_ITEM_EFFECT ORDER BY choice_id, position")
    suspend fun readChoiceItemEffect(): List<ChoiceItemEffectEntity>

    @Insert
    suspend fun insertChoiceItemEffect(rows: List<ChoiceItemEffectEntity>)

}
