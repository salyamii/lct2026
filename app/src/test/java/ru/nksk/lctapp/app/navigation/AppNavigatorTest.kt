package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    private data object Home : NavKey
    private data class Detail(val id: String) : NavKey

    @Test
    fun twoDifferentMenuCallbacksOnlyOpenTheFirstDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)
        val openFirst = { navigator.navigate(source = Home, destination = Detail("first")) }
        val openSecond = { navigator.navigate(source = Home, destination = Detail("second")) }

        openFirst()
        openSecond()

        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun repeatedTapsDoNotDuplicateTheCurrentDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        navigator.navigate(source = Home, destination = Detail("first"))
        navigator.navigate(source = Home, destination = Detail("first"))
        navigator.goBack(source = Detail("first"))

        assertEquals(listOf(Home), stack)
    }

    @Test
    fun differentArgumentsCreateDistinctEntriesAndBackRestoresThePreviousOne() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        navigator.navigate(source = Home, destination = Detail("first"))
        navigator.navigate(source = Detail("first"), destination = Detail("second"))
        assertEquals(listOf(Home, Detail("first"), Detail("second")), stack)

        navigator.goBack(source = Detail("second"))
        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun backAtRootNeverRemovesTheStartDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        repeat(3) { navigator.goBack() }

        assertEquals(listOf(Home), stack)
    }

    @Test
    fun navigatingToTheCurrentDestinationKeepsASingleEntry() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"))
        val navigator = AppNavigator(stack)

        navigator.navigate(source = Detail("first"), destination = Detail("first"))

        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun staleFeatureBackCannotPopANewerDestination() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"))
        val navigator = AppNavigator(stack)
        val backFromFirst = { navigator.goBack(source = Detail("first")) }

        navigator.navigate(source = Detail("first"), destination = Detail("second"))
        backFromFirst()

        assertEquals(listOf(Home, Detail("first"), Detail("second")), stack)
    }

    @Test
    fun repeatedFeatureBackOnlyRemovesItsOwnEntry() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"), Detail("second"))
        val navigator = AppNavigator(stack)
        val backFromSecond = { navigator.goBack(source = Detail("second")) }

        backFromSecond()
        backFromSecond()

        assertEquals(listOf(Home, Detail("first")), stack)
    }

    @Test
    fun featureBackAtRootNeverRemovesTheStartDestination() {
        val stack = mutableListOf<NavKey>(Home)
        val navigator = AppNavigator(stack)

        navigator.goBack(source = Home)

        assertEquals(listOf(Home), stack)
    }

    @Test
    fun systemBackRemovesTheCurrentDestination() {
        val stack = mutableListOf<NavKey>(Home, Detail("first"), Detail("second"))
        val navigator = AppNavigator(stack)

        navigator.goBack()

        assertEquals(listOf(Home, Detail("first")), stack)
    }
}
