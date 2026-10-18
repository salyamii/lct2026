package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs

/** Three substantial repairs, without a timer or a motor-skill penalty. */
data class WheelRepair(
    val fixedMask: Int = 0,
    val selectedSection: Int = 0,
    val angle: Float = INITIAL_ANGLES[0],
) {
    val completed: Boolean get() = fixedMask == COMPLETE_MASK
    val fixedCount: Int get() = (0 until SECTION_COUNT).count(::isFixed)
    val aligned: Boolean get() = abs(normalizeAngle(angle)) <= ALIGN_TOLERANCE
    fun isFixed(section: Int): Boolean = section in 0 until SECTION_COUNT && fixedMask and (1 shl section) != 0

    fun rotateBy(delta: Float): WheelRepair =
        if (completed || !delta.isFinite()) this else copy(angle = normalizeAngle(angle + delta))

    fun select(section: Int): WheelRepair =
        if (section !in 0 until SECTION_COUNT || isFixed(section) || section == selectedSection) this
        else copy(selectedSection = section, angle = INITIAL_ANGLES[section])

    fun secure(): WheelRepair {
        if (completed || !aligned) return this
        val updatedMask = fixedMask or (1 shl selectedSection)
        val next = (0 until SECTION_COUNT).firstOrNull { updatedMask and (1 shl it) == 0 }
        return copy(fixedMask = updatedMask, selectedSection = next ?: selectedSection,
            angle = next?.let { INITIAL_ANGLES[it] } ?: 0f)
    }

    companion object {
        const val SECTION_COUNT = 3
        const val COMPLETE_MASK = 7
        const val ALIGN_TOLERANCE = 14f
        private val INITIAL_ANGLES = listOf(90f, -150f, -60f)

        fun normalizeAngle(value: Float): Float = ((value % 360f + 540f) % 360f) - 180f

        fun restore(mask: Int, selected: Int, angle: Float): WheelRepair {
            val safeMask = mask and COMPLETE_MASK
            val safeSelected = selected.takeIf { it in 0 until SECTION_COUNT && safeMask and (1 shl it) == 0 }
                ?: (0 until SECTION_COUNT).firstOrNull { safeMask and (1 shl it) == 0 } ?: 0
            return WheelRepair(safeMask, safeSelected,
                if (angle.isFinite() && safeSelected == selected) normalizeAngle(angle) else INITIAL_ANGLES[safeSelected])
        }
    }
}
