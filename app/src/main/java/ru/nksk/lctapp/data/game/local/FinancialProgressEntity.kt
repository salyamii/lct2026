package ru.nksk.lctapp.data.game.local

import androidx.room3.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nksk.lctapp.domain.economy.BudgetPlan
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.finance.*

@Entity(tableName = "FINANCIAL_PERIOD", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
], indices = [Index(value = ["game_state_id", "position"], unique = true)])
internal data class FinancialPeriodEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    val position: Int,
    @ColumnInfo(name = "goal_id") val goalId: String,
    val ordinal: Int,
    @ColumnInfo(name = "started_day") val startedDay: Int,
    @ColumnInfo(name = "opening_available") val openingAvailable: Long,
    @ColumnInfo(name = "opening_savings") val openingSavings: Long,
    @ColumnInfo(name = "closed_day") val closedDay: Int?,
    @ColumnInfo(name = "needs_provided") val needsProvided: Boolean,
    @ColumnInfo(name = "independently_saved") val independentlySaved: Boolean,
    @ColumnInfo(name = "reviewed_plan") val reviewedPlan: Boolean,
    val imported: Boolean,
    val income: Long = 0,
    @ColumnInfo(name = "spent_available") val spentAvailable: Long = 0,
    @ColumnInfo(name = "spent_savings") val spentSavings: Long = 0,
    val deposited: Long = 0,
    val withdrawn: Long = 0,
    @ColumnInfo(name = "savings_practice_payload") val savingsPracticePayload: String? = null,
    @ColumnInfo(name = "review_evidence_payload") val reviewEvidencePayload: String? = null,
)

@Entity(tableName = "FINANCIAL_CURSOR", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
    ForeignKey(entity = FinancialPeriodEntity::class, parentColumns = ["id"], childColumns = ["period_id"]),
], indices = [Index(value = ["period_id"])])
internal data class FinancialCursorEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "period_id") val periodId: String?,
)

@Entity(tableName = "BUDGET_PLAN_REVISION", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
    ForeignKey(entity = FinancialPeriodEntity::class, parentColumns = ["id"], childColumns = ["period_id"]),
], indices = [Index(value = ["game_state_id", "position"], unique = true), Index(value = ["period_id"])])
internal data class BudgetPlanRevisionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    val position: Int,
    @ColumnInfo(name = "period_id") val periodId: String?,
    val ordinal: Int,
    val day: Int,
    @ColumnInfo(name = "available_basis") val availableBasis: Long,
    val needs: Long, val wants: Long, val savings: Long, val reserve: Long,
    val reason: String,
    @ColumnInfo(name = "previous_id") val previousId: String?,
    @ColumnInfo(name = "known_needs") val knownNeeds: Long?,
    @ColumnInfo(name = "cause_action_id") val causeActionId: String?,
)

/** Authored immutable task snapshot, with mutable attempt fields stored separately. */
@Entity(tableName = "FINANCIAL_PRACTICE", foreignKeys = [
    ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"]),
])
internal data class FinancialPracticeEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "task_payload") val taskPayload: String,
    @ColumnInfo(name = "answered_option_id") val answeredOptionId: String?,
    @ColumnInfo(name = "used_hint") val usedHint: Boolean,
    val attempts: Int,
)

@Dao
internal interface FinancialProgressDao {
    @Query("SELECT * FROM FINANCIAL_PERIOD WHERE game_state_id = :gameId ORDER BY position")
    suspend fun periods(gameId: String): List<FinancialPeriodEntity>
    @Query("SELECT * FROM BUDGET_PLAN_REVISION WHERE game_state_id = :gameId ORDER BY position")
    suspend fun plans(gameId: String): List<BudgetPlanRevisionEntity>
    @Query("SELECT * FROM FINANCIAL_CURSOR WHERE game_state_id = :gameId")
    suspend fun cursor(gameId: String): FinancialCursorEntity?
    @Query("SELECT * FROM FINANCIAL_PRACTICE WHERE game_state_id = :gameId")
    suspend fun practice(gameId: String): FinancialPracticeEntity?
    @Upsert suspend fun writePeriods(rows: List<FinancialPeriodEntity>)
    @Insert suspend fun appendPlans(rows: List<BudgetPlanRevisionEntity>)
    @Upsert suspend fun writeCursor(row: FinancialCursorEntity)
    @Upsert suspend fun writePractice(row: FinancialPracticeEntity)
    @Query("DELETE FROM FINANCIAL_PRACTICE WHERE game_state_id = :gameId") suspend fun deletePractice(gameId: String)
    @Query("DELETE FROM FINANCIAL_CURSOR") suspend fun clearCursor()
    @Query("DELETE FROM FINANCIAL_PRACTICE") suspend fun clearPractice()
    @Query("DELETE FROM BUDGET_PLAN_REVISION") suspend fun clearPlans()
    @Query("DELETE FROM FINANCIAL_PERIOD") suspend fun clearPeriods()
}

internal fun FinancialPeriodEntity.toDomain() = FinancialPeriod(id, goalId, ordinal, startedDay,
    openingAvailable, openingSavings, closedDay, needsProvided, independentlySaved, reviewedPlan, imported,
    income, spentAvailable, spentSavings, deposited, withdrawn,
    savingPractice = savingsPracticePayload?.let { financialPracticeJson.decodeFromString<SavingsPracticeState>(it) },
    reviewEvidence = reviewEvidencePayload?.let { financialPracticeJson.decodeFromString<PeriodReviewEvidence>(it) })

internal fun FinancialPeriod.toEntity(position: Int) = FinancialPeriodEntity(id, CURRENT_GAME_ID, position,
    goalId, ordinal, startedDay, openingAvailable, openingSavings, closedDay, needsProvided, independentlySaved, reviewedPlan, imported,
    income, spentAvailable, spentSavings, deposited, withdrawn,
    savingsPracticePayload = savingPractice?.let { financialPracticeJson.encodeToString(it) },
    reviewEvidencePayload = reviewEvidence?.let { financialPracticeJson.encodeToString(it) })

private val financialPracticeJson = Json { encodeDefaults = true; ignoreUnknownKeys = false }

internal fun BudgetPlanRevisionEntity.toDomain() = BudgetPlanRevision(id, periodId, ordinal, day, availableBasis,
    BudgetPlan(needs, wants, savings, reserve), BudgetRevisionReason.valueOf(reason), previousId, knownNeeds, causeActionId)

internal fun BudgetPlanRevision.toEntity(position: Int) = BudgetPlanRevisionEntity(id, CURRENT_GAME_ID, position,
    periodId, ordinal, day, availableBasis, allocation.needs, allocation.wants, allocation.savings,
    allocation.reserve, reason.name, previousId, knownNeeds, causeActionId)
