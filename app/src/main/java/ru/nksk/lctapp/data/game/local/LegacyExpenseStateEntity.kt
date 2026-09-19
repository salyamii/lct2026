package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey

/** Retains the expenses branch's save fields without assigning them engine semantics. */
@Entity(
    tableName = "LEGACY_EXPENSE_STATE",
    foreignKeys = [ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"])],
)
internal data class LegacyExpenseStateEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "expense_sequence") val expenseSequence: Long,
    @ColumnInfo(name = "expense_stage") val expenseStage: String,
    @ColumnInfo(name = "earning_attempt") val earningAttempt: Long,
)
