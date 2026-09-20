package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** A historical finale/project association. Ownership-based collection progress is not persisted here. */
@Entity(tableName = "COMPLETED_GOAL_PROJECT", foreignKeys = [
    ForeignKey(entity = PlayerDecisionEntity::class, parentColumns = ["id"], childColumns = ["decision_id"]),
    ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goal_id"]),
], indices = [Index(value = ["goal_id"])])
internal data class CompletedGoalProjectEntity(
    @PrimaryKey @ColumnInfo(name = "decision_id") val decisionId: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
)
