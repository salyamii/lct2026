package ru.nksk.lctapp.feature.day.ui

import java.math.BigInteger
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.DaySummary
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.pet.renderPetText

internal data class DaySummaryRow(val label: String, val value: String = "")
internal data class DaySummaryUiState(
    val activities: List<DaySummaryRow>,
    val moneyLines: List<String>,
    val remaining: String,
    val detailsNote: String? = null,
    val adjustmentNote: String? = null,
)

internal fun DaySummary.toUiState(catalog: GameCatalog, petName: String): DaySummaryUiState {
    fun text(value: String) = renderPetText(value, petName)
    fun itemName(id: String) = text(catalog.content.items.find { it.id == id }?.name ?: "Предмет")
    fun eventName(id: String) = text(catalog.content.events.find { it.id == id }?.title ?: "Событие")
    val usedReceipts = mutableSetOf<String>()
    fun takeReceipt(source: String, kinds: Set<DayJournalKind>): DayJournalEntry? = journal.firstOrNull {
        it.id !in usedReceipts && it.sourceId == source && it.kind in kinds
    }?.also { usedReceipts += it.id }
    val choiceKinds = setOf(DayJournalKind.EVENT_CHOICE, DayJournalKind.DEED)
    val rows = mutableListOf<DaySummaryRow>()
    val describedEvents = mutableSetOf<String>()

    fun completedAction(choiceId: String, receipt: DayJournalEntry?) {
        val choice = catalog.content.choices.find { it.id == choiceId }
        if (choice == null) {
            rows += DaySummaryRow("Завершили событие", receipt?.let { actionMoney(it.moneyDelta) }.orEmpty())
            return
        }
        val event = catalog.content.events.single { it.id == choice.eventId }
        describedEvents += event.id
        val items = catalog.content.choiceItemEffects.filter {
            it.choiceId == choice.id && it.operation == ItemOperation.ADD
        }.sortedBy { it.position }.map { it.itemId }
        // These items belong to this committed choice; show them once alongside its result.
        items.forEach { takeReceipt(it, setOf(DayJournalKind.ITEM_RECEIVED)) }
        val purchase = event.type == EventType.WANT && choice.moneyDelta < 0 && items.isNotEmpty()
        val label = if (purchase) "Купили: ${items.joinToString { itemName(it) }}" else
            catalog.cards[event.id]?.summaryByChoiceId?.get(choice.id)?.let(::text)
                ?: if (event.type == EventType.EARNING) "Выполнили дело «${text(event.title)}»"
                else "${text(event.title)}: ${text(choice.text)}"
        val details = buildList {
            receipt?.let { actionMoney(it.moneyDelta).takeIf(String::isNotEmpty)?.let(::add) }
            if (!purchase && items.isNotEmpty()) add("Получили: ${items.joinToString { itemName(it) }}")
        }
        rows += DaySummaryRow(label, details.joinToString(". "))
    }

    // Occurrences and committed decisions also exist in saves from before the money journal.
    // Only today's completed choices enter this list; deferred offers never become achievements.
    // When journaling was introduced mid-day, earlier decisions have no receipts. Match from
    // the end so a newly recorded payout is never attributed to an older repeat of the same job.
    val availableChoiceReceipts = journal.filter { it.kind in choiceKinds }
        .groupBy { it.sourceId }.mapValues { it.value.toMutableList() }
    val receiptsByDecision = completedDecisions.asReversed().associate { decision ->
        decision.id to availableChoiceReceipts[decision.choiceId]?.removeLastOrNull()
    }
    completedDecisions.forEach { decision ->
        val receipt = receiptsByDecision[decision.id]
        receipt?.let { usedReceipts += it.id }
        completedAction(decision.choiceId, receipt)
    }
    journal.forEach { entry ->
        if (!usedReceipts.add(entry.id)) return@forEach
        when (entry.kind) {
            DayJournalKind.EVENT_CHOICE, DayJournalKind.DEED -> completedAction(entry.sourceId, entry)
            DayJournalKind.MEAL -> rows += if (entry.moneyDelta == 0L)
                DaySummaryRow("Поели в бесплатной столовой", "Завтра будет меньше сил")
                else DaySummaryRow("Пообедали", actionMoney(entry.moneyDelta))
            DayJournalKind.ITEM_PURCHASE -> rows += DaySummaryRow("Купили: ${itemName(entry.sourceId)}", actionMoney(entry.moneyDelta))
            DayJournalKind.ITEM_RECEIVED -> rows += DaySummaryRow("Получили: ${itemName(entry.sourceId)}")
            DayJournalKind.WEEKLY_INCOME -> rows += DaySummaryRow("Получили монеты на новую неделю", actionMoney(entry.moneyDelta))
            DayJournalKind.EVENT_START -> rows += DaySummaryRow(eventName(entry.sourceId), actionMoney(entry.moneyDelta))
        }
    }
    // Compatibility for callers that only have the earlier summary projection.
    completedLoreEventIds.filterNot { it in describedEvents }.forEach {
        rows += DaySummaryRow("Продвинулись в истории «${eventName(it)}»")
    }

    val spending = journal.filter { it.moneyDelta < 0 }.fold(BigInteger.ZERO) { total, it -> total - it.moneyDelta.toBigInteger() }
    val income = journal.filter { it.moneyDelta > 0 }.fold(BigInteger.ZERO) { total, it -> total + it.moneyDelta.toBigInteger() }
    val difference = closingBalance.toBigInteger() - openingBalance.toBigInteger() - balanceAdjustment.toBigInteger()
    val balanced = income - spending == difference
    val moneyLines = buildList {
        if (balanced) {
            if (spending.signum() > 0) add("Потрачено за день: ${coins(spending)}")
            if (income.signum() > 0) add("Получено за день: ${coins(income)}")
        } else {
            // The old net change cannot establish gross spending: earnings may have offset costs.
            if (difference.signum() < 0) add("За день монет стало меньше на ${difference.abs()}")
            if (difference.signum() > 0) add("За день монет стало больше на $difference")
        }
    }
    return DaySummaryUiState(
        activities = rows,
        moneyLines = moneyLines,
        remaining = "Сейчас ${coins(closingBalance.toBigInteger())}",
        detailsNote = if (!balanced) "Не все доходы и траты за этот день сохранились в подробностях." else null,
        adjustmentNote = if (balanceAdjustment > 0)
            "При переходе на бюджет добавлено ${coins(balanceAdjustment.toBigInteger(), accusative = true)}. Это не заработок за день." else null,
    )
}

private fun actionMoney(value: Long): String = when {
    value < 0 -> "Потратили ${coins(value.toBigInteger().abs(), accusative = true)}"
    value > 0 -> "Получили ${coins(value.toBigInteger(), accusative = true)}"
    else -> ""
}

private fun coins(value: BigInteger, accusative: Boolean = false): String {
    val lastHundred = (value.abs() % 100.toBigInteger()).toInt()
    val lastDigit = lastHundred % 10
    val unit = when {
        lastHundred in 11..14 -> "монет"
        lastDigit == 1 -> if (accusative) "монету" else "монета"
        lastDigit in 2..4 -> "монеты"
        else -> "монет"
    }
    return "$value $unit"
}
