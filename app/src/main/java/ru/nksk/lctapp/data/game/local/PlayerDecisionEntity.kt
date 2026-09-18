package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "PLAYER_DECISION",
    foreignKeys = [
        ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
        ForeignKey(entity = EventChoiceEntity::class, parentColumns = ["id"], childColumns = ["choice_id"]),
    ],
    indices = [
        Index(value = ["game_state_id", "position"], unique = true),
        Index(value = ["choice_id"]),
    ],
)
internal data class PlayerDecisionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "choice_id") val choiceId: String,
)
