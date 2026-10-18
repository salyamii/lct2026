package ru.nksk.lctapp.domain.minigame.chapterone

enum class SignalLight { RED, AMBER, BLUE }
data class SignalState(val entered: List<SignalLight> = emptyList(), val checked: Boolean = false) {
    val complete: Boolean get() = checked && entered == SignalGame.code
    val needsCorrection: Boolean get() = checked && !complete
}

object SignalGame {
    // Test-scene order matches the red, amber and blue legend on the chapter's plate.
    val code = listOf(SignalLight.RED, SignalLight.AMBER, SignalLight.BLUE)
    fun choose(state: SignalState, light: SignalLight): SignalState =
        if (state.complete || state.entered.size == code.size) state
        else state.copy(entered = state.entered + light, checked = false)
    fun undo(state: SignalState): SignalState =
        if (state.complete) state else state.copy(entered = state.entered.dropLast(1), checked = false)
    fun send(state: SignalState): SignalState =
        if (state.entered.size == code.size) state.copy(checked = true) else state
    fun restore(values: List<Int>, checked: Boolean): SignalState {
        if (values.size > code.size || values.any { it !in SignalLight.entries.indices }) return SignalState()
        return SignalState(values.map { SignalLight.entries[it] }, checked && values.size == code.size)
    }
}
