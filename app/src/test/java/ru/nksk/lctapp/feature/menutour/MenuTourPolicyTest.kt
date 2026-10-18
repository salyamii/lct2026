package ru.nksk.lctapp.feature.menutour

import org.junit.Assert.*
import org.junit.Test

class MenuTourPolicyTest {
    @Test fun petAndNameHaveSeparateHighlightsAndOnlyPetUsesBottomCard() {
        assertEquals(listOf("menu.pet"), MenuTourSteps[0].targets)
        assertTrue(MenuTourSteps[0].cardBelowTarget)
        assertEquals(listOf("menu.name"), MenuTourSteps[1].targets)
        assertFalse(MenuTourSteps[1].cardBelowTarget)
        assertEquals((0 until TourFinished).toList(), MenuTourSteps.map { it.index })
    }

    @Test fun newPlayerStartsButExistingPlayerIsNotInterrupted() {
        assertEquals(0, initialTourStep(savedStep = null, newPlayer = true))
        assertEquals(12, initialTourStep(savedStep = null, newPlayer = false))
    }

    @Test fun savedProgressWinsOverGameAgeAndRestoresAfterRestart() {
        assertEquals(4, initialTourStep(savedStep = 4, newPlayer = false))
        assertEquals(12, initialTourStep(savedStep = 12, newPlayer = true))
    }

    @Test fun contextualHintsWaitForTheirActualTargets() {
        assertNull(visibleTourStep(10, emptySet()))
        assertEquals(10, visibleTourStep(10, setOf("menu.status"))?.index)
        assertNull(visibleTourStep(11, setOf("menu.status")))
        assertEquals(11, visibleTourStep(11, setOf("menu.meal"))?.index)
        assertNull(visibleTourStep(12, setOf("menu.status", "menu.meal")))
    }

    @Test fun mainTourWaitsForLayoutAndDoesNotSkipMissingTargets() {
        assertNull(visibleTourStep(6, emptySet()))
        assertEquals(6, visibleTourStep(6, setOf("menu.tasks"))?.index)
    }

    @Test fun goalHintUsesTheBottomGoalButtonWithoutTheRemovedHeader() {
        assertNull(visibleTourStep(4, emptySet()))
        assertEquals(listOf("menu.goal"), visibleTourStep(4, setOf("menu.goal"))?.targets)
        assertFalse(MenuTourSteps.flatMap { it.targets }.contains("menu.goal.summary"))
    }
}
