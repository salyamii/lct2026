package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import ru.nksk.lctapp.domain.economy.BudgetPlanning
import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetPlanningStage

/** Optional session: absence means the allocation has been confirmed. */
@Entity(tableName = "BUDGET_PLANNING", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
])
internal data class BudgetPlanningEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "stage") val stage: String,
    @ColumnInfo(name = "income") val income: Long,
    @ColumnInfo(name = "revision") val revision: Long,
)

private val reasons = StoredCode(mapOf(
    BudgetPlanningReason.MANUAL to "MANUAL",
    BudgetPlanningReason.INITIAL to "INITIAL",
    BudgetPlanningReason.WEEKLY to "WEEKLY",
    BudgetPlanningReason.MIGRATION to "MIGRATION",
))
private val stages = StoredCode(mapOf(
    BudgetPlanningStage.RECEIPT to "RECEIPT",
    BudgetPlanningStage.ALLOCATION to "ALLOCATION",
))

internal fun BudgetPlanning.toEntity() = BudgetPlanningEntity(
    CURRENT_GAME_ID, id, reasons.encode(reason), stages.encode(stage), income, revision,
)
internal fun BudgetPlanningEntity.toDomain() = BudgetPlanning(
    id = sessionId, reason = reasons.decode(reason), stage = stages.decode(stage), income = income, revision = revision,
)
