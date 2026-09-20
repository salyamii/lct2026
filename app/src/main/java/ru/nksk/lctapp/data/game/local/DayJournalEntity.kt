package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind

@Entity(
    tableName = "DAY_JOURNAL",
    foreignKeys = [ForeignKey(entity = EngineStateEntity::class, parentColumns = ["game_state_id"], childColumns = ["game_state_id"])],
    indices = [Index(value = ["game_state_id", "position"], unique = true)],
)
internal data class DayJournalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    val position: Int,
    val kind: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "money_delta") val moneyDelta: Long,
    @ColumnInfo(name = "energy_delta") val energyDelta: Int,
)

internal fun DayJournalEntity.toDomain() = DayJournalEntry(id, DayJournalKind.valueOf(kind), sourceId, moneyDelta, energyDelta)
