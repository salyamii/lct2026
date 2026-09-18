package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "EVENT",
    foreignKeys = [
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["next_chapter_id"]),
    ],
    indices = [
        Index(value = ["next_chapter_id"]),
    ],
)
internal data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "min_satiety") val minSatiety: Int?,
    @ColumnInfo(name = "max_fatigue") val maxFatigue: Int?,
    @ColumnInfo(name = "pet_state_on_start") val petStateOnStart: String?,
    @ColumnInfo(name = "money_delta_on_start") val moneyDeltaOnStart: Long,
    @ColumnInfo(name = "budget_section_on_start") val budgetSectionOnStart: String?,
    @ColumnInfo(name = "next_chapter_id") val nextChapterId: String?,
)
