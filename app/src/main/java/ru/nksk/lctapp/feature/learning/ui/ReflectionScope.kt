package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.domain.timemachine.TimeMachineAvailability
import ru.nksk.lctapp.domain.timemachine.TimeMachineStatus

internal enum class ReflectionScope { DAY, WEEK }
internal enum class LearningPageMode { HISTORY, TRAINING, REFLECTION }

/** The selected week chooses memories; each comparison still ends within its original day. */
internal fun TimeMachineAvailability.forReflection(day: Int?, scope: ReflectionScope): TimeMachineAvailability {
    if (day == null || status != TimeMachineStatus.READY) return this
    val first = if (scope == ReflectionScope.DAY) day else ((day - 1) / 7) * 7 + 1
    val selected = decisions.filter { it.day?.let { value -> value in first..day } == true }
    return copy(decisions = selected, reason = if (selected.isEmpty())
        if (scope == ReflectionScope.DAY) "В этот день пока нет решений, для которых можно посмотреть другой путь."
        else "На этой неделе пока нет решений, для которых можно посмотреть другой путь."
        else null)
}
