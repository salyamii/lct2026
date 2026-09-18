package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "GAME_DAY",
    foreignKeys = [
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapter_id"]),
    ],
    indices = [
        Index(value = ["chapter_id"]),
    ],
)
internal data class GameDayEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "day_number") val dayNumber: Int,
)
