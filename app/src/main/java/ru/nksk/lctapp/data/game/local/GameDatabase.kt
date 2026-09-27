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
        BudgetPlanningEntity::class,
        PlayerDecisionEntity::class,
        OwnedItemEntity::class,
        EngineStateEntity::class,
        EngineEventEntity::class,
        EngineDeedEntity::class,
        LegacyExpenseStateEntity::class,
        MiniGameCompletionEntity::class,
        OnboardingDraftEntity::class,
        GoalSelectionEntity::class,
        SavingGoalSelectionEntity::class,
        CompletedGoalProjectEntity::class,
        DayJournalEntity::class,
        GameRunEntity::class,
        GameAuditEntity::class,
        AuditFactIdEntity::class,
        AuditOutboxEntity::class,
        FinancialPeriodEntity::class,
        FinancialCursorEntity::class,
        BudgetPlanRevisionEntity::class,
        FinancialPracticeEntity::class,
        EventExposureEntity::class,
        BackendSyncStateEntity::class,
        PendingBackendRequestEntity::class,
    ],
    version = 21,
    exportSchema = true,
)
internal abstract class GameDatabase : RoomDatabase() {
    abstract fun onboardingDraftDao(): OnboardingDraftDao
    abstract fun gameStateDao(): GameStateDao
    abstract fun storyContentDao(): StoryContentDao
    abstract fun gameHistoryDao(): GameHistoryDao
    abstract fun financialProgressDao(): FinancialProgressDao
    abstract fun backendSyncDao(): BackendSyncDao

    companion object {
        const val FILE_NAME = "ryzhik-game.db"

        fun open(context: Context, name: String = FILE_NAME): GameDatabase =
            Room.databaseBuilder<GameDatabase>(context.applicationContext, name)
                .setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                .build()
    }
}
