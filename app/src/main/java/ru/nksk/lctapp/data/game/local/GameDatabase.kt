package ru.nksk.lctapp.data.game.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

@Database(
    entities = [
        GoalEntity::class,
        ItemEntity::class,
        ChapterEntity::class,
        GameDayEntity::class,
        EventEntity::class,
        DayEventEntity::class,
        EventChoiceEntity::class,
        GoalRequiredItemEntity::class,
        EventItemEffectEntity::class,
        ChoiceItemEffectEntity::class,
        GameStateEntity::class,
        PlayerDecisionEntity::class,
        OwnedItemEntity::class,
        EngineStateEntity::class,
        EngineEventEntity::class,
        EngineDeedEntity::class,
        LegacyExpenseStateEntity::class,
        MiniGameCompletionEntity::class,
        OnboardingDraftEntity::class,
        GoalSelectionEntity::class,
        CompletedGoalProjectEntity::class,
        DayJournalEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
internal abstract class GameDatabase : RoomDatabase() {
    abstract fun onboardingDraftDao(): OnboardingDraftDao
    abstract fun gameStateDao(): GameStateDao
    abstract fun storyContentDao(): StoryContentDao

    companion object {
        const val FILE_NAME = "ryzhik-game.db"

        fun open(context: Context, name: String = FILE_NAME): GameDatabase =
            Room.databaseBuilder<GameDatabase>(context.applicationContext, name)
                .setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                .build()
    }
}
