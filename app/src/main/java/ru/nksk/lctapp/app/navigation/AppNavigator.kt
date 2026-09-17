package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey

/** Navigation policy, independent of UI and of the storage used for the back stack. */
internal class AppNavigator(private val backStack: MutableList<NavKey>) {
    fun navigate(source: NavKey, destination: NavKey) {
        // Check the stack immediately; the outgoing entry may still be resumed.
        if (backStack.lastOrNull() != source) return

        // Single-top navigation: repeated taps must not add the same screen twice.
        if (source != destination) {
            backStack.add(destination)
        }
    }

    fun goBack(source: NavKey) {
        if (backStack.lastOrNull() == source) {
            goBack()
        }
    }

    /** System Back targets the current stack, independently of an entry callback. */
    fun goBack() {
        // NavDisplay lets the activity handle Back at the root; never empty its stack.
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
