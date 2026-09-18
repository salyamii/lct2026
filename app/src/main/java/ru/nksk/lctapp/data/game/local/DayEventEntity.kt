package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "DAY_EVENT",
    foreignKeys = [
        ForeignKey(entity = GameDayEntity::class, parentColumns = ["id"], childColumns = ["day_id"]),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"]),
    ],
    indices = [
        Index(value = ["day_id", "position"], unique = true),
        Index(value = ["event_id"]),
    ],
)
internal data class DayEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "day_id") val dayId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "event_id") val eventId: String,
)
