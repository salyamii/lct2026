package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "EVENT_CHOICE",
    foreignKeys = [
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"]),
    ],
    indices = [
        Index(value = ["event_id", "position"], unique = true),
    ],
)
internal data class EventChoiceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "money_delta") val moneyDelta: Long,
    @ColumnInfo(name = "budget_section") val budgetSection: String?,
    @ColumnInfo(name = "pet_state_after") val petStateAfter: String?,
    @ColumnInfo(name = "goal_impact") val goalImpact: String,
)
