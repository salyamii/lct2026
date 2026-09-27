package ru.nksk.lctapp.data.game.local

import androidx.room3.*

/** Only the player's chosen item is stored; price, chapter and progress are derived. */
@Entity(tableName = "SAVING_GOAL_SELECTION", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
    ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"]),
], indices = [Index(value = ["item_id"])])
internal data class SavingGoalSelectionEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
)
