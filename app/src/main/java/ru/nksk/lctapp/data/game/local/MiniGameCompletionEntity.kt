package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Durable receipt: retrying a successful attempt must not apply its costs twice. */
@Entity(
    tableName = "MINI_GAME_COMPLETION",
    foreignKeys = [ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"])],
    indices = [Index(value = ["game_state_id"])],
)
internal data class MiniGameCompletionEntity(
    @PrimaryKey @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
)
