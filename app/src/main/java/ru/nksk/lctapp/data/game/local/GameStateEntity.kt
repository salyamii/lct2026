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
    @ColumnInfo(name = "balance") val balance: Long,
    @ColumnInfo(name = "planned_needs") val plannedNeeds: Long,
    @ColumnInfo(name = "planned_wants") val plannedWants: Long,
    @ColumnInfo(name = "planned_savings") val plannedSavings: Long,
    @ColumnInfo(name = "planned_reserve") val plannedReserve: Long,
    @ColumnInfo(name = "current_day_id") val currentDayId: String?,
    @ColumnInfo(name = "next_script_position") val nextScriptPosition: Int?,
    @ColumnInfo(name = "active_event_id") val activeEventId: String?,
)
