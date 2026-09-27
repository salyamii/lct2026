package ru.nksk.lctapp.feature.learning.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.domain.timemachine.TimeMachineAvailability
import ru.nksk.lctapp.domain.timemachine.TimeMachineDecision
import ru.nksk.lctapp.domain.timemachine.TimeMachineStatus

class ReflectionScopeTest {
    private fun availability(vararg days: Int?) = TimeMachineAvailability(
        status = TimeMachineStatus.READY,
        decisions = days.mapIndexed { index, day ->
            TimeMachineDecision("entry-$index", index.toLong() + 1, day, "Решение $index", emptyList())
        },
    )

    @Test fun selectedDayExcludesEarlierFutureAndUnknownDaysButPreservesDecisionOrder() {
        val source = availability(8, 9, null, 10, 9)
        val filtered = source.forReflection(9, ReflectionScope.DAY)

        assertEquals(listOf(source.decisions[1], source.decisions[4]), filtered.decisions)
        assertEquals(TimeMachineStatus.READY, filtered.status)
        assertNull(filtered.reason)
        assertEquals(5, source.decisions.size)
    }

    @Test fun weekStartsAtTheCurrentSevenDayBoundaryAndNeverIncludesFutureDays() {
        val source = availability(1, 7, 8, 9, 10, 11, 14, null)

        assertEquals(listOf(8, 9, 10), source.forReflection(10, ReflectionScope.WEEK).decisions.map { it.day })
        assertEquals(listOf(8), source.forReflection(8, ReflectionScope.WEEK).decisions.map { it.day })
        assertEquals(listOf(1, 7), source.forReflection(7, ReflectionScope.WEEK).decisions.map { it.day })
    }

    @Test fun missingDayOnlyProducesAnEmptyViewAndDoesNotInventAReplayFailure() {
        val source = availability(1, 2, 15)
        val day = source.forReflection(9, ReflectionScope.DAY)
        val week = source.forReflection(9, ReflectionScope.WEEK)

        assertEquals(TimeMachineStatus.READY, day.status)
        assertTrue(day.decisions.isEmpty())
        assertEquals("В этот день пока нет решений, для которых можно посмотреть другой путь.", day.reason)
        assertTrue(week.decisions.isEmpty())
        assertEquals("На этой неделе пока нет решений, для которых можно посмотреть другой путь.", week.reason)
    }

    @Test fun unavailableHistoryKeepsItsActualReasonInsteadOfAFalseEmptyDayMessage() {
        val unavailable = TimeMachineAvailability(TimeMachineStatus.INCOMPATIBLE_VERSION,
            reason = "Правила этой истории отличаются.")

        assertSame(unavailable, unavailable.forReflection(8, ReflectionScope.WEEK))
        val unscoped = availability(1, null, 8)
        assertSame(unscoped, unscoped.forReflection(null, ReflectionScope.DAY))
    }
}
