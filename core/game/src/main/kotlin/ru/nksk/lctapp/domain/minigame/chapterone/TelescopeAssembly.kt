package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs
import kotlin.random.Random

enum class TelescopePart { TRIPOD, TUBE, LENS }
enum class TelescopeStage { TRIPOD, TUBE, EYEPIECE, FOCUS, COMPLETE }

/** Local assembly progress. Completing it does not itself grant a campaign reward. */
data class TelescopeAssembly(
    val installedCount: Int = 0,
    val selectedPart: TelescopePart? = null,
    val focus: Float = .18f,
    val completed: Boolean = false,
    // Kept for existing completion validation; seating a part now secures it immediately.
    val firstFastener: Float = 0f,
    val secondFastener: Float = 0f,
    val selectedFastener: Int = 0,
    val mountSecured: Boolean = false,
    val eyepieceTurn: Float = 0f,
    val eyepieceSecured: Boolean = false,
    val skyVariant: Int = 0,
    val focusTarget: Float = FOCUS_TARGET,
) {
    val stage: TelescopeStage get() = when {
        installedCount == 0 -> TelescopeStage.TRIPOD
        installedCount == 1 -> TelescopeStage.TUBE
        installedCount == 2 -> TelescopeStage.EYEPIECE
        completed -> TelescopeStage.COMPLETE
        else -> TelescopeStage.FOCUS
    }
    val nextPart: TelescopePart? get() = when (stage) {
        TelescopeStage.TRIPOD -> TelescopePart.TRIPOD
        TelescopeStage.TUBE -> TelescopePart.TUBE
        TelescopeStage.EYEPIECE -> TelescopePart.LENS
        else -> null
    }
    val assembled: Boolean get() = installedCount == 3 && mountSecured && eyepieceSecured
    val sharpness: Float get() = (1f - abs(focus - focusTarget) / .32f).coerceIn(0f, 1f)
    val inFocus: Boolean get() = assembled && abs(focus - focusTarget) <= FOCUS_TOLERANCE

    fun select(part: TelescopePart): TelescopeAssembly =
        if (nextPart == null || part.ordinal < installedCount) this else copy(selectedPart = part)

    fun install(part: TelescopePart): TelescopeAssembly {
        if (completed || part != nextPart) return this
        val count = installedCount + 1
        return copy(
            installedCount = count,
            selectedPart = null,
            firstFastener = if (count >= 2) MOUNT_TURN else 0f,
            secondFastener = if (count >= 2) MOUNT_TURN else 0f,
            mountSecured = count >= 2,
            eyepieceTurn = if (count == 3) EYEPIECE_TURN else 0f,
            eyepieceSecured = count == 3,
        )
    }

    fun adjustFocus(value: Float): TelescopeAssembly =
        if (!assembled || completed || !value.isFinite()) this else copy(focus = value.coerceIn(0f, 1f))

    fun observe(): TelescopeAssembly = if (inFocus) copy(completed = true) else this

    companion object {
        const val FOCUS_TARGET = .68f
        const val FOCUS_TOLERANCE = .085f
        const val MOUNT_TURN = 180f
        const val EYEPIECE_TURN = 270f
        const val SKY_VARIANT_COUNT = 5
        const val SAVE_VERSION = 4
        private val FOCUS_TARGETS = listOf(.24f, .39f, .54f, .69f, .84f)

        fun create(random: Random = Random.Default, previous: TelescopeAssembly? = null): TelescopeAssembly {
            val skies = (0 until SKY_VARIANT_COUNT).filter { it != previous?.skyVariant }
            val targets = FOCUS_TARGETS.filter {
                previous == null || !previous.focusTarget.isFinite() ||
                    abs(it - previous.focusTarget) > FOCUS_TOLERANCE * 2
            }
            return TelescopeAssembly(
                focus = .02f,
                skyVariant = skies.random(random),
                focusTarget = targets.random(random),
            )
        }

        fun restore(
            installed: Int, selected: Int?, focus: Float, completed: Boolean,
            version: Int = SAVE_VERSION,
            skyVariant: Int = 0,
            focusTarget: Float = FOCUS_TARGET,
        ): TelescopeAssembly {
            if (version !in 1..SAVE_VERSION) return TelescopeAssembly()
            val count = installed.coerceIn(0, TelescopePart.entries.size)
            val validFocus = focus.isFinite() && focus in 0f..1f
            val safeFocus = if (focus.isFinite()) focus.coerceIn(0f, 1f) else .18f
            val validSky = skyVariant in 0 until SKY_VARIANT_COUNT
            val validTarget = focusTarget.isFinite() && focusTarget in 0f..1f
            val legacy = version < 4
            // Older saves may be midway through a removed tightening step. Keep all
            // installed parts and continue at the next placement or focus adjustment.
            val state = TelescopeAssembly(count, TelescopePart.entries.getOrNull(selected ?: -1)
                ?.takeIf { it.ordinal >= count }, safeFocus,
                firstFastener = if (count >= 2) MOUNT_TURN else 0f,
                secondFastener = if (count >= 2) MOUNT_TURN else 0f,
                mountSecured = count >= 2,
                eyepieceTurn = if (count == 3) EYEPIECE_TURN else 0f,
                eyepieceSecured = count == 3,
                skyVariant = if (legacy || !validSky) 0 else skyVariant,
                focusTarget = if (legacy || !validTarget) FOCUS_TARGET else focusTarget,
            )
            return state.copy(completed = completed && validFocus &&
                (legacy || (validSky && validTarget)) && state.inFocus)
        }
    }
}
