package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "GOAL_REQUIRED_ITEM",
    primaryKeys = ["goal_id", "item_id"],
    foreignKeys = [
        ForeignKey(entity = GoalEntity::class, parentColumns = ["id"], childColumns = ["goal_id"]),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"]),
    ],
    indices = [
        Index(value = ["item_id"]),
    ],
)
internal data class GoalRequiredItemEntity(
    @ColumnInfo(name = "goal_id") val goalId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
)
