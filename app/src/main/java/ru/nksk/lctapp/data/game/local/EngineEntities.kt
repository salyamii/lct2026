package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "ENGINE_STATE",
    foreignKeys = [ForeignKey(entity = GameStateEntity::class, parentColumns = ["id"], childColumns = ["game_state_id"])],
)
internal data class EngineStateEntity(
    @PrimaryKey @ColumnInfo(name = "game_state_id") val gameStateId: String,
    @ColumnInfo(name = "rules_id") val rulesId: String,
    val revision: Long,
    val day: Int,
    val phase: String,
    val steps: Int,
    val energy: Int,
    @ColumnInfo(name = "ate_today") val ateToday: Boolean,
    @ColumnInfo(name = "next_morning_energy") val nextMorningEnergy: Int?,
    @ColumnInfo(name = "opening_balance") val openingBalance: Long,
    @ColumnInfo(name = "opening_energy") val openingEnergy: Int? = null,
    @ColumnInfo(name = "balance_adjustment", defaultValue = "0") val balanceAdjustment: Long = 0,
)

@Entity(
    tableName = "ENGINE_DEED",
    foreignKeys = [
        ForeignKey(entity = EngineStateEntity::class, parentColumns = ["game_state_id"], childColumns = ["game_state_id"]),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"]),
    ],
    indices = [Index(value = ["game_state_id", "position"], unique = true), Index(value = ["event_id"])],
)
internal data class EngineDeedEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    val position: Int,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "expires_day") val expiresDay: Int,
    val completed: Boolean,
)

@Entity(
    tableName = "ENGINE_EVENT",
    foreignKeys = [
        ForeignKey(entity = EngineStateEntity::class, parentColumns = ["game_state_id"], childColumns = ["game_state_id"]),
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"], childColumns = ["event_id"]),
        ForeignKey(entity = EngineDeedEntity::class, parentColumns = ["id"], childColumns = ["deed_offer_id"]),
    ],
    indices = [Index(value = ["game_state_id", "position"], unique = true), Index(value = ["event_id"]), Index(value = ["deed_offer_id"], unique = true)],
)
internal data class EngineEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "game_state_id") val gameStateId: String,
    val position: Int,
    @ColumnInfo(name = "event_id") val eventId: String?,
    val status: String,
    @ColumnInfo(name = "deed_offer_id") val deedOfferId: String?,
)
