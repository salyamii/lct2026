package ru.nksk.lctapp.data.game.local

import androidx.room3.*
import ru.nksk.lctapp.domain.engine.EventExposure

@Entity(tableName = "EVENT_EXPOSURE", primaryKeys = ["game_state_id", "event_id"],
    foreignKeys = [
        ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"]),
    ], indices = [Index(value = ["game_state_id", "position"], unique = true), Index(value = ["event_id"])])
internal data class EventExposureEntity(
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    val position: Int,
    @ColumnInfo(name = "last_offered_day") val lastOfferedDay: Int?,
    @ColumnInfo(name = "last_completed_day") val lastCompletedDay: Int?,
    @ColumnInfo(name = "offer_count") val offerCount: Int,
    @ColumnInfo(name = "completion_count") val completionCount: Int,
)

internal fun EventExposureEntity.toDomain() = EventExposure(eventId, lastOfferedDay, lastCompletedDay, offerCount, completionCount)
internal fun EventExposure.toEntity(position: Int) = EventExposureEntity(CURRENT_GAME_ID, eventId, position,
    lastOfferedDay, lastCompletedDay, offerCount, completionCount)
