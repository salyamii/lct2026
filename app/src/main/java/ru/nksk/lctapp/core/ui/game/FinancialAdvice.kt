package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.engine.EventStatus
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.DeedGameKind

/** General teaching copy, never an answer, a gameplay rule or evidence of mastery. */
internal enum class FinancialAdviceTopic(val text: String) {
    // FIN-01
    COMPARE("Перед покупкой сравни цену с деньгами, которые у тебя есть. Так поймёшь, хватает ли на неё и останется ли что-то ещё."),
    // FIN-02, FIN-04
    PLAN("Деньги могут закончиться раньше следующего поступления, даже если каждая покупка кажется небольшой. План помогает оставить на то, что понадобится в ближайшие дни."),
    // FIN-03
    NEEDS("Еда нужна каждый день, а понравившаяся вещь иногда может подождать. Если заранее оставить деньги на необходимое, будет понятнее, сколько можно потратить на желания."),
    // FIN-03, FIN-05, FIN-06: wording approved by the user, kept verbatim.
    WANTS("Если тратить все монеты на то, что хочется прямо сейчас, на большую цель ничего не останется. Но без маленьких радостей копить бывает скучно. Можно часть монет откладывать, а часть оставлять на приятные покупки."),
    // FIN-05, FIN-06
    SAVING("Даже небольшие суммы в копилке постепенно приближают большую покупку. Если откладывать понемногу, не придётся искать всю сумму сразу."),
    // FIN-07, FIN-08
    RESERVE("Запас помогает оплатить то, чего не ожидал, например ремонт. После такой траты менять план нормально: на все прежние покупки денег уже может не хватить."),
    // FIN-07, FIN-05
    RESERVE_OR_GOAL("В копилке деньги лежат для большой цели, а в запасе - на неожиданные траты. Запас помогает оплатить ремонт, не забирая деньги у цели."),
    // FIN-09
    RESOURCES("Если сделать самому, деньги останутся, но энергии на другие дела станет меньше. Оплата помощи бережёт энергию, зато оставляет меньше монет на покупки."),
    // FIN-10
    EARNING("Дополнительное дело может приблизить желанную покупку, но требует энергии и времени. Пока работа не закончена, её награду ещё нельзя потратить."),
    // FIN-11
    REFLECTION("Если денег не хватило на важную покупку, вспомни, на что они ушли. Так в следующий раз будет проще решить, что купить сейчас, а что оставить на потом."),
    // FIN-12
    LEDGER("Когда помнишь, откуда пришли деньги и куда ушли, легче понять, сколько осталось. Перевод в копилку не покупка: эти монеты всё ещё твои."),
}

internal data class FinancialAdviceUi(val id: String, val text: String)

/** A stable four-day cadence. Reopening or editing amounts cannot reroll a reminder. */
internal fun financialAdvice(
    day: Int,
    surface: String,
    offset: Int,
    vararg topics: FinancialAdviceTopic,
    first: Boolean = false,
): FinancialAdviceUi? {
    if (topics.isEmpty()) return null
    val index = (day.coerceAtLeast(1) - 1).toLong()
    if (!first && (index + offset) % 4L != 0L) return null
    val topic = topics[(index / 4L % topics.size).toInt()]
    return FinancialAdviceUi("$surface:${day.coerceAtLeast(1)}:${topic.name}", topic.text)
}

/** Uses only the current day's ordered occurrences, never the persistent journal. */
internal fun eventFinancialAdvice(state: GameState, catalog: GameCatalog): FinancialAdviceUi? {
    val day = state.engine ?: return null
    val occurrence = day.currentEvent ?: return null
    val eligible = day.events.mapNotNull { event ->
        eventAdviceTopic(event.eventId, catalog)?.let { event.id to it }
    }
    val index = eligible.indexOfFirst { it.first == occurrence.id }
    if (index < 0 || (day.day.toLong() - 1L + index) % 4L != 0L) return null
    // A deed proposal may use RESULT; its caller distinguishes proposals from completed cards.
    if (occurrence.status !in setOf(EventStatus.ACTIVE, EventStatus.RESULT)) return null
    val topic = eligible[index].second
    return FinancialAdviceUi("event:${day.day}:${occurrence.id}:${topic.name}", topic.text)
}

private fun eventAdviceTopic(eventId: String, catalog: GameCatalog): FinancialAdviceTopic? {
    val event = catalog.content.events.firstOrNull { it.id == eventId } ?: return null
    val policy = catalog.policies[eventId] ?: return null
    val choices = catalog.content.choices.filter { it.eventId == eventId && it.id !in policy.disabledChoiceIds }
    return when {
        event.type == EventType.EARNING && policy.deedGameKind == DeedGameKind.COMPARISON -> FinancialAdviceTopic.COMPARE
        event.type == EventType.EARNING -> FinancialAdviceTopic.EARNING
        event.type == EventType.WANT && choices.any { it.moneyDelta < 0 } ->
            if (policy.feedsPetChoiceIds.isNotEmpty()) FinancialAdviceTopic.NEEDS else FinancialAdviceTopic.WANTS
        choices.any { paid -> choices.any { other ->
            paid.moneyDelta < other.moneyDelta && policy.energyFor(paid.id) < policy.energyFor(other.id)
        } } -> FinancialAdviceTopic.RESOURCES
        event.type == EventType.RANDOM && choices.any { it.moneyDelta < 0 } -> FinancialAdviceTopic.RESERVE
        else -> null
    }
}
