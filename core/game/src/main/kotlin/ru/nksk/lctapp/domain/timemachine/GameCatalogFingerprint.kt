package ru.nksk.lctapp.domain.timemachine

import java.lang.reflect.Modifier
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.history.HistoryCodec

/** Includes gameplay and legacy copy. Presentation-only media can evolve without invalidating replay. */
object GameCatalogFingerprint {
    /** Increment when transition semantics change, even if authored content stays identical. */
    const val TRANSITION_VERSION = 11
    fun compute(catalog: GameCatalog): String {
        val content = catalog.content
        val normalized = catalog.copy(content = content.copy(
            chapters = content.chapters.sortedBy { it.id }, days = content.days.sortedBy { it.id },
            schedule = content.schedule.sortedBy { it.id }, events = content.events.sortedBy { it.id },
            choices = content.choices.sortedBy { it.id }, items = content.items.sortedBy { it.id },
            goals = content.goals.sortedBy { it.id },
            requiredItems = content.requiredItems.sortedWith(compareBy({ it.goalId }, { it.itemId })),
            eventItemEffects = content.eventItemEffects.sortedBy { it.id },
            choiceItemEffects = content.choiceItemEffects.sortedBy { it.id },
        ))
        return "catalog-v1:transition-$TRANSITION_VERSION:${HistoryCodec.sha256(canonical(normalized))}"
    }

    private fun token(value: String) = "${value.length}:$value"
    private fun canonical(value: Any?): String = when (value) {
        null -> "null"
        is String -> "string${token(value)}"
        is Number -> "number${token(value.javaClass.name)}${token(value.toString())}"
        is Boolean -> "boolean:$value"
        is Enum<*> -> "enum${token(value.javaClass.name)}${token(value.name)}"
        is List<*> -> "list${token(value.joinToString("") { token(canonical(it)) })}"
        is Set<*> -> "set${token(value.map(::canonical).sorted().joinToString("") { token(it) })}"
        is Map<*, *> -> "map${token(value.entries.map { canonical(it.key) to canonical(it.value) }
            .sortedBy { it.first }.joinToString("") { token(it.first) + token(it.second) })}"
        else -> {
            require(value.javaClass.name.startsWith("ru.nksk.lctapp.domain.")) { "Unsupported catalog value" }
            val fields = value.javaClass.declaredFields.filterNot {
                Modifier.isStatic(it.modifiers) || it.isSynthetic ||
                    (value is ru.nksk.lctapp.domain.engine.EventCardCopy && it.name == "presentation")
            }
                .sortedBy { it.name }
            "record${token(value.javaClass.name)}${token(fields.joinToString("") { field ->
                field.isAccessible = true
                token(field.name) + token(canonical(field.get(value)))
            })}"
        }
    }
}
