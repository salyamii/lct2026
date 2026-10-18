package ru.nksk.lctapp.domain.minigame.chapterone

import kotlin.math.abs

enum class TelescopePart { TRIPOD, TUBE, LENS }
enum class TelescopeStage { TRIPOD, TUBE, MOUNT, EYEPIECE, THREAD, FOCUS, COMPLETE }

/** Temporary practice state. It never represents a purchase or a campaign reward. */
data class TelescopeAssembly(
    val installedCount: Int = 0,
    val selectedPart: TelescopePart? = null,
    val focus: Float = .18f,
    val completed: Boolean = false,
    val firstFastener: Float = 0f,
    val secondFastener: Float = 0f,
    val selectedFastener: Int = 0,
    val mountSecured: Boolean = false,
    val eyepieceTurn: Float = 0f,
    val eyepieceSecured: Boolean = false,
) {
    val stage: TelescopeStage get() = when {
        installedCount == 0 -> TelescopeStage.TRIPOD
        installedCount == 1 -> TelescopeStage.TUBE
        !mountSecured -> TelescopeStage.MOUNT
        installedCount == 2 -> TelescopeStage.EYEPIECE
        !eyepieceSecured -> TelescopeStage.THREAD
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
    val mountReady: Boolean get() = firstFastener >= MOUNT_TURN - TURN_TOLERANCE && secondFastener >= MOUNT_TURN - TURN_TOLERANCE
    val eyepieceReady: Boolean get() = eyepieceTurn >= EYEPIECE_TURN - TURN_TOLERANCE
    val sharpness: Float get() = (1f - abs(focus - FOCUS_TARGET) / .32f).coerceIn(0f, 1f)
    val inFocus: Boolean get() = assembled && abs(focus - FOCUS_TARGET) <= FOCUS_TOLERANCE

    fun select(part: TelescopePart): TelescopeAssembly =
        if (nextPart == null || part.ordinal < installedCount) this else copy(selectedPart = part)

    fun install(part: TelescopePart): TelescopeAssembly =
        if (!completed && part == nextPart) copy(installedCount = installedCount + 1, selectedPart = null)
        else this

    fun selectFastener(index: Int): TelescopeAssembly =
        if (stage == TelescopeStage.MOUNT && index in 0..1) copy(selectedFastener = index) else this

    fun turnFastener(index: Int, degrees: Float): TelescopeAssembly {
        if (stage != TelescopeStage.MOUNT || index !in 0..1 || !degrees.isFinite()) return this
        return if (index == 0) copy(firstFastener = (firstFastener + degrees).coerceIn(0f, MOUNT_TURN), selectedFastener = index)
        else copy(secondFastener = (secondFastener + degrees).coerceIn(0f, MOUNT_TURN), selectedFastener = index)
    }

    fun secureMount(): TelescopeAssembly =
        if (stage == TelescopeStage.MOUNT && mountReady) copy(mountSecured = true,
            firstFastener = MOUNT_TURN, secondFastener = MOUNT_TURN) else this

    fun turnEyepiece(degrees: Float): TelescopeAssembly =
        if (stage != TelescopeStage.THREAD || !degrees.isFinite()) this
        else copy(eyepieceTurn = (eyepieceTurn + degrees).coerceIn(0f, EYEPIECE_TURN))

    fun secureEyepiece(): TelescopeAssembly =
        if (stage == TelescopeStage.THREAD && eyepieceReady) copy(eyepieceSecured = true, eyepieceTurn = EYEPIECE_TURN) else this

    /** Release, rather than an intermediate pointer frame, advances a finished connection. */
    fun finishTurning(): TelescopeAssembly = when (stage) {
        TelescopeStage.MOUNT -> secureMount()
        TelescopeStage.THREAD -> secureEyepiece()
        else -> this
    }

    fun adjustFocus(value: Float): TelescopeAssembly =
        if (!assembled || completed || !value.isFinite()) this else copy(focus = value.coerceIn(0f, 1f))

    fun observe(): TelescopeAssembly = if (inFocus) copy(completed = true) else this

    companion object {
        const val FOCUS_TARGET = .68f
        const val FOCUS_TOLERANCE = .085f
        const val MOUNT_TURN = 180f
        const val EYEPIECE_TURN = 270f
        const val TURN_TOLERANCE = 8f
        const val SAVE_VERSION = 2

        fun restore(
            installed: Int, selected: Int?, focus: Float, completed: Boolean,
            firstFastener: Float = 0f, secondFastener: Float = 0f, selectedFastener: Int = 0,
            mountSecured: Boolean = false, eyepieceTurn: Float = 0f, eyepieceSecured: Boolean = false,
            version: Int = SAVE_VERSION,
        ): TelescopeAssembly {
            if (version != SAVE_VERSION) return TelescopeAssembly()
            val count = installed.coerceIn(0, TelescopePart.entries.size)
            val safeFocus = if (focus.isFinite()) focus.coerceIn(0f, 1f) else .18f
            fun safeTurn(value: Float, limit: Float) = if (value.isFinite()) value.coerceIn(0f, limit) else 0f
            val first = if (count >= 2) safeTurn(firstFastener, MOUNT_TURN) else 0f
            val second = if (count >= 2) safeTurn(secondFastener, MOUNT_TURN) else 0f
            val securedMount = mountSecured && first == MOUNT_TURN && second == MOUNT_TURN
            val safeCount = if (count == 3 && !securedMount) 2 else count
            val thread = if (safeCount == 3) safeTurn(eyepieceTurn, EYEPIECE_TURN) else 0f
            val state = TelescopeAssembly(safeCount, TelescopePart.entries.getOrNull(selected ?: -1)
                ?.takeIf { it.ordinal >= safeCount }, safeFocus,
                firstFastener = first, secondFastener = second,
                selectedFastener = selectedFastener.coerceIn(0, 1), mountSecured = securedMount,
                eyepieceTurn = thread, eyepieceSecured = eyepieceSecured && thread == EYEPIECE_TURN && safeCount == 3)
            return state.copy(completed = completed && state.inFocus)
        }
    }
}
