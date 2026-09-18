package ru.nksk.lctapp.feature.tasks.ui

import androidx.compose.ui.graphics.Color

/**
 * Палитра раздела «Дела»: ночная обсерватория + кремовые листы событий,
 * лаймовые действия и янтарные чипы — по макетам event-карточек.
 * Шрифты и базовая тема берутся из core.ui.
 */
object DeedColors {
    val Scene = Color(0xFF120F30)          // ночная сцена за листами
    val Cream = Color(0xFFF7F1E4)          // кремовый лист события
    val CreamCard = Color(0xFFFFFCF1)      // карточка внутри листа
    val CreamSoft = Color(0xFFEFE7D4)      // приглушённый фон плиток
    val Text = Color(0xFF33306E)           // индиго-текст на креме
    val TextSoft = Color(0xFF8A85B8)       // вторичный текст
    val Lime = Color(0xFFA8E830)           // основное действие
    val Chip = Color(0xFFF6C445)           // янтарный чип награды/цены
    val ChipSoft = Color(0xFFF1E3B2)       // мягкий янтарный чип
    val White = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5DCC6)
    val Board = Color(0xFF322E7E)          // игровое поле
}
