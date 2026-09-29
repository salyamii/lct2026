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

    /** Cross-links between singleton feature screens reuse their existing entry. */
    fun navigateToExisting(source: NavKey, destination: NavKey) {
        if (backStack.lastOrNull() != source) return
        val existing = backStack.indexOfLast { it == destination }
        if (existing >= 0) {
            backStack.subList(existing + 1, backStack.size).clear()
        } else {
            backStack.add(destination)
        }
    }

    /** Reuse a lower singleton without discarding the in-progress prerequisite above it. */
    fun moveToTop(source: NavKey, destination: NavKey) {
        if (backStack.lastOrNull() != source || source == destination) return
        val existing = backStack.indexOfLast { it == destination }
        if (existing >= 0) backStack.removeAt(existing)
        backStack.add(destination)
    }

    fun goBack(source: NavKey) {
        if (backStack.lastOrNull() == source) {
            goBack()
        }
    }

    fun returnToRoot(source: NavKey) {
        if (backStack.lastOrNull() == source && backStack.size > 1) {
            backStack.subList(1, backStack.size).clear()
        }
    }

    /** A budget opened by a chapter review returns to that review, keeping its accepted answers. */
    fun finishBudget(source: NavKey, chapterPractice: NavKey) {
        if (backStack.lastOrNull() != source) return
        if (backStack.dropLast(1).contains(chapterPractice)) navigateToExisting(source, chapterPractice)
        else returnToRoot(source)
    }

    /** Finish a prerequisite screen without leaving it beneath a newly opened story screen. */
    fun returnToOrReplace(source: NavKey, destination: NavKey) {
        if (backStack.lastOrNull() != source) return
        if (backStack.contains(destination)) navigateToExisting(source, destination)
        else replace(source, destination)
    }

    fun replace(source: NavKey, destination: NavKey) {
        if (backStack.lastOrNull() == source && backStack.size > 1) {
            backStack[backStack.lastIndex] = destination
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
