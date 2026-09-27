package ru.nksk.lctapp.data.game.local

import androidx.room3.*

@Entity(tableName = "ONBOARDING_DRAFT")
internal data class OnboardingDraftEntity(
    @PrimaryKey val id: String = CURRENT_GAME_ID,
    val name: String,
    val temperament: String,
    val fur: String,
    @ColumnInfo(defaultValue = "'PROFILE'") val step: String = "PROFILE",
    @ColumnInfo(name = "accessory_id", defaultValue = "'BACKPACK'") val accessoryId: String = "BACKPACK",
    @ColumnInfo(name = "goal_id") val goalId: String? = null,
    @ColumnInfo(name = "saving_item_id") val savingItemId: String? = null,
)

@Dao
internal interface OnboardingDraftDao {
    @Query("SELECT * FROM ONBOARDING_DRAFT WHERE id = 'current'")
    suspend fun read(): OnboardingDraftEntity?
    @Upsert suspend fun save(draft: OnboardingDraftEntity)
    @Query("DELETE FROM ONBOARDING_DRAFT") suspend fun clear()
}
