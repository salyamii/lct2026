package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "GAME_STATE",
    foreignKeys = [
        ForeignKey(entity = GameDayEntity::class, parentColumns = ["id"], childColumns = ["current_day_id"]),
        ForeignKey(entity = DayEventEntity::class, parentColumns = ["day_id", "position"], childColumns = ["current_day_id", "next_script_position"]),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["active_event_id"]),
    ],
    indices = [
        Index(value = ["current_day_id"]),
        Index(value = ["current_day_id", "next_script_position"]),
        Index(value = ["active_event_id"]),
    ],
)
internal data class GameStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "visual_state") val visualState: String,
    @ColumnInfo(name = "selected_look") val selectedLook: String,
    @ColumnInfo(name = "satiety") val satiety: Int,
    @ColumnInfo(name = "fatigue") val fatigue: Int,
    @ColumnInfo(name = "unallocated") val unallocated: Long,
    @ColumnInfo(name = "needs") val needs: Long,
    @ColumnInfo(name = "wants") val wants: Long,
    @ColumnInfo(name = "savings") val savings: Long,
    @ColumnInfo(name = "reserve") val reserve: Long,
    @ColumnInfo(name = "available_balance", defaultValue = "0") val availableBalance: Long,
    @ColumnInfo(name = "savings_balance", defaultValue = "0") val savingsBalance: Long,
    /** Storage-only discriminator; historical GameState payloads retain their original wire shape. */
    @ColumnInfo(name = "budget_model_version", defaultValue = "0") val budgetModelVersion: Int = 1,
    @ColumnInfo(name = "current_day_id") val currentDayId: String?,
    @ColumnInfo(name = "next_script_position") val nextScriptPosition: Int?,
    @ColumnInfo(name = "active_event_id") val activeEventId: String?,
    @ColumnInfo(name = "pet_temperament") val petTemperament: String? = null,
    @ColumnInfo(name = "pet_name", defaultValue = "'Рыжик'") val petName: String,
    @ColumnInfo(name = "pet_age", defaultValue = "'CUB'") val petAge: String,
    @ColumnInfo(name = "pet_color", defaultValue = "'COPPER'") val petColor: String,
    @ColumnInfo(name = "location_id", defaultValue = "'city'") val locationId: String = "city",
    @ColumnInfo(name = "location_lighting", defaultValue = "'DAY'") val locationLighting: String = "DAY",
)
