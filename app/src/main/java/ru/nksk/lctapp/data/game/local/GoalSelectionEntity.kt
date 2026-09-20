package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** An optional player choice, independent of the day template's chapter. No derived progress. */
@Entity(tableName = "GOAL_SELECTION", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
    ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goal_id"]),
], indices = [Index(value = ["goal_id"])])
internal data class GoalSelectionEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
)
