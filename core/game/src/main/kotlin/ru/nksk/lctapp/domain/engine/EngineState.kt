package ru.nksk.lctapp.domain.engine

/** Calendar days are independent of authored story days and of the number of actions. */
data class EngineState(
    val rulesId: String,
    val revision: Long,
    val day: Int,
    val phase: DayPhase,
    val steps: Int,
    val energy: Int,
    val ateToday: Boolean,
    val nextMorningEnergy: Int?,
    val openingBalance: Long,
    val events: List<EventOccurrence>,
    val deeds: List<DeedOffer>,
) {
    init {
        require(rulesId.isNotBlank())
        require(revision >= 0 && day >= 1 && steps >= 0 && energy >= 0)
        require(nextMorningEnergy == null || nextMorningEnergy >= 0)
        require(events.map { it.id }.distinct().size == events.size)
        require(deeds.map { it.id }.distinct().size == deeds.size)
        require(events.count { it.status == EventStatus.ACTIVE || it.status == EventStatus.RESULT } <= 1)
        require(events.all { (it.origin == EventOrigin.DEED) == (it.deedOfferId != null) })
        val executingOffers = events.mapNotNull { it.deedOfferId }
        require(executingOffers.distinct().size == executingOffers.size)
        require(events.all { event -> event.deedOfferId == null || deeds.any { it.id == event.deedOfferId && it.eventId == event.eventId } })
        require(events.none { it.origin == EventOrigin.DEED && (it.status == EventStatus.PENDING || it.status == EventStatus.CARRIED) })
        require(phase != DayPhase.FINISHED || currentEvent == null)
    }

    val currentEvent: EventOccurrence?
        get() = events.singleOrNull { it.status == EventStatus.ACTIVE || it.status == EventStatus.RESULT }
}

enum class DayPhase { RUNNING, READY_TO_END, FINISHED }
enum class EventStatus { PENDING, ACTIVE, RESULT, COMPLETED, CARRIED }
enum class EventOrigin { SCHEDULE, DEED }

data class EventOccurrence(
    val id: String,
    val eventId: String,
    val origin: EventOrigin,
    val status: EventStatus,
    val deedOfferId: String? = null,
)

data class DeedOffer(
    val id: String,
    val eventId: String,
    val expiresDay: Int,
    val completed: Boolean = false,
) {
    init { require(expiresDay >= 1) }
    fun isAvailable(day: Int) = !completed && day <= expiresDay
}

data class DaySummary(
    val day: Int,
    val openingBalance: Long,
    val closingBalance: Long,
    val completedLoreEventIds: List<String>,
    val steps: Int,
)
