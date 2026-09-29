package ru.nksk.lctapp.feature.menutour

import org.junit.Assert.*
import org.junit.Test

class MenuTourPolicyTest {
    @Test fun newPlayerStartsButExistingPlayerIsNotInterrupted() {
        assertEquals(0, initialTourStep(savedStep = null, newPlayer = true))
        assertEquals(11, initialTourStep(savedStep = null, newPlayer = false))
    }

    @Test fun savedProgressWinsOverGameAgeAndRestoresAfterRestart() {
        assertEquals(4, initialTourStep(savedStep = 4, newPlayer = false))
        assertEquals(11, initialTourStep(savedStep = 11, newPlayer = true))
    }

    @Test fun contextualHintsWaitForTheirActualTargets() {
        assertNull(visibleTourStep(9, emptySet()))
        assertEquals(9, visibleTourStep(9, setOf("menu.status"))?.index)
        assertNull(visibleTourStep(10, setOf("menu.status")))
        assertEquals(10, visibleTourStep(10, setOf("menu.meal"))?.index)
        assertNull(visibleTourStep(11, setOf("menu.status", "menu.meal")))
    }

    @Test fun mainTourWaitsForLayoutAndDoesNotSkipMissingTargets() {
        assertNull(visibleTourStep(5, emptySet()))
        assertEquals(5, visibleTourStep(5, setOf("menu.tasks"))?.index)
    }
}
