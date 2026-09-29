package ru.nksk.lctapp.domain.engine

/** Only explicit scheduling/replacement declarations relate different event identities. */
internal class EventFamilyIndex(
    policies: Map<String, EventPolicy>,
    replacements: Map<String, String> = emptyMap(),
) {
    private val families = policies.mapValuesTo(mutableMapOf()) { (id, policy) ->
        policy.scheduling.family ?: id
    }.apply {
        policies.forEach { (id, policy) ->
            policy.scheduling.previousEventIds.forEach { previous -> this[previous] = getValue(id) }
        }
        replacements.forEach { (previous, current) ->
            this[previous] = getValue(current)
        }
    }

    fun family(id: String): String = families[id] ?: id
}
