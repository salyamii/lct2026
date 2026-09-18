package ru.nksk.lctapp.data.game.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "CHOICE_ITEM_EFFECT",
    foreignKeys = [
        ForeignKey(entity = EventChoiceEntity::class, parentColumns = ["id"], childColumns = ["choice_id"]),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["item_id"]),
    ],
    indices = [
        Index(value = ["choice_id", "position"], unique = true),
        Index(value = ["item_id"]),
    ],
)
internal data class ChoiceItemEffectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "choice_id") val choiceId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "operation") val operation: String,
)
