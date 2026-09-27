package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.core.ui.game.asGameActionLabel
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.backend.ParentRewardOutcome
import ru.nksk.lctapp.domain.backend.ParentRewardPayload
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.displayTitle
import ru.nksk.lctapp.domain.engine.displayAction
import ru.nksk.lctapp.domain.engine.displayOutcome
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.pet.renderPetText

/** Names come from content; amounts come only from committed receipts, never catalog prices. */
internal fun learningHistoryRows(history: List<AuditEntry>, catalog: GameCatalog, petName: String,
    includeDay: Boolean = true): List<String> =
    history.asReversed().asSequence().flatMap { it.activityRows(catalog, petName, includeDay).asSequence() }.take(50).toList()

private fun AuditEntry.activityRows(catalog: GameCatalog, petName: String, includeDay: Boolean): List<String> {
    // A baseline is a checkpoint, not evidence that all its older choices happened now.
    val before = before ?: return emptyList()
    val after = after ?: return emptyList()
    fun text(value: String) = renderPetText(value, petName).asGameUiText()
    fun itemName(id: String) = text(catalog.content.items.find { it.id == id }?.name ?: "Предмет")
    fun eventName(id: String) = text(catalog.content.events.find { it.id == id }?.let(catalog::displayTitle) ?: "Событие")
    parentReward?.let { application ->
        if (application.receipt.outcome == ParentRewardOutcome.ALREADY_OWNED) return emptyList()
        val label = when (val gift = application.reward.reward) {
            is ParentRewardPayload.Coins -> listOfNotNull("Подарок от родителя",
                operations.singleOrNull { it.kind == LedgerKind.INCOME }?.moneyText()).joinToString(". ")
            is ParentRewardPayload.Accessory -> "Подарок от родителя: ${itemName(gift.itemId)}"
        }
        return listOf(if (includeDay) "День ${after.engine?.day ?: 1}: $label" else label)
    }
    val oldJournalIds = before.engine?.journal.orEmpty().map { it.id }.toSet()
    val journal = after.engine?.journal.orEmpty().filter { it.id !in oldJournalIds }
    val receipts = operations.associateBy { it.operationId }
    val usedJournal = mutableSetOf<String>()
    val usedReceipts = mutableSetOf<String>()
    val oldOwnedIds = before.ownedItems.map { it.id }.toSet()
    val newItems = after.ownedItems.filter { it.id !in oldOwnedIds }.toMutableList()
    fun takeItem(id: String): String? {
        val index = newItems.indexOfFirst { it.itemId == id }
        return if (index < 0) null else itemName(newItems.removeAt(index).itemId)
    }
    fun receipt(entry: DayJournalEntry?): LedgerEntry? {
        entry ?: return null
        usedJournal += entry.id
        return receipts[entry.id]?.takeIf { usedReceipts.add(it.operationId) }
    }
    fun purchasePayment(entry: DayJournalEntry): String? {
        val primary = receipt(entry) ?: return null
        // A mixed goal purchase has one journal entry and two canonical account receipts.
        val fromAvailable = receipts["${entry.id}:available"]?.takeIf {
            primary.kind == LedgerKind.SAVINGS_EXPENSE && it.kind == LedgerKind.AVAILABLE_EXPENSE &&
                usedReceipts.add(it.operationId)
        }
        return if (fromAvailable != null) {
            "Потратили ${primary.amount.coinAmount()} из копилки и ${fromAvailable.amount.coinAmount()} из текущих денег"
        } else primary.moneyText()
    }
    val rows = mutableListOf<String>()
    fun add(label: String, receipt: LedgerEntry? = null, detail: String? = null) {
        rows += listOfNotNull(label, receipt?.moneyText(), detail).joinToString(". ")
    }
    fun completedChoice(choiceId: String, entry: DayJournalEntry?) {
        val choice = catalog.content.choices.find { it.id == choiceId }
        val event = choice?.let { definition -> catalog.content.events.find { it.id == definition.eventId } }
        val received = catalog.content.choiceItemEffects.filter { it.choiceId == choiceId && it.operation == ItemOperation.ADD }
            .sortedBy { it.position }.mapNotNull { takeItem(it.itemId) }
        val purchase = event?.type == EventType.WANT && choice.moneyDelta < 0 && received.isNotEmpty()
        val label = when {
            purchase -> "Купили: ${received.joinToString()}"
            choice == null || event == null -> "Завершили событие"
            else -> catalog.displayOutcome(choice)?.let(::text)
                ?: if (event.type == EventType.EARNING) "Выполнили дело «${text(catalog.displayTitle(event))}»"
                else "${text(catalog.displayTitle(event))}: ${text(catalog.displayAction(choice).asGameActionLabel())}"
        }
        add(label, receipt(entry), received.takeIf { !purchase && it.isNotEmpty() }?.let { "Получили: ${it.joinToString()}" })
    }

    val oldDecisionIds = before.story.decisions.map { it.id }.toSet()
    after.story.decisions.asReversed().filter { it.id !in oldDecisionIds }.forEach { decision ->
        val entry = journal.lastOrNull { it.id !in usedJournal && it.sourceId == decision.choiceId &&
            (it.kind == DayJournalKind.EVENT_CHOICE || it.kind == DayJournalKind.DEED) }
        completedChoice(decision.choiceId, entry)
    }
    journal.asReversed().forEach { entry ->
        if (entry.id in usedJournal) return@forEach
        when (entry.kind) {
            DayJournalKind.EVENT_CHOICE, DayJournalKind.DEED -> completedChoice(entry.sourceId, entry)
            DayJournalKind.ITEM_PURCHASE -> {
                takeItem(entry.sourceId)
                add("Купили: ${itemName(entry.sourceId)}", detail = purchasePayment(entry))
            }
            // Inventory occurrences below also cover acquisitions without a journal entry.
            DayJournalKind.ITEM_RECEIVED -> Unit
            DayJournalKind.MEAL -> {
                val meal = catalog.meals.find { it.id == entry.sourceId }
                add(if (meal?.price == 0L) "Поели в бесплатной столовой" else "Пообедали", receipt(entry))
            }
            DayJournalKind.WEEKLY_INCOME -> add("Получили монеты на новую неделю", receipt(entry))
            DayJournalKind.PARENT_REWARD -> add("Подарок от родителя", receipt(entry))
            DayJournalKind.EVENT_START -> add(eventName(entry.sourceId), receipt(entry))
        }
    }
    // Transfers have their own canonical receipts and no daily journal charge.
    operations.asReversed().filter { it.operationId !in usedReceipts }.forEach { add(it.moneyText()) }
    newItems.asReversed().forEach { add("Получили: ${itemName(it.itemId)}") }
    return if (includeDay) rows.map { "День ${after.engine?.day ?: 1}: $it" } else rows
}

private fun LedgerEntry.moneyText(): String {
    val action = when (kind) {
        LedgerKind.INCOME -> "Получили"
        LedgerKind.AVAILABLE_EXPENSE -> "Потратили"
        LedgerKind.SAVINGS_EXPENSE -> "Потратили из копилки"
        LedgerKind.DEPOSIT -> "Отложили в копилку"
        LedgerKind.WITHDRAWAL -> "Взяли из копилки"
    }
    return "$action ${amount.coinAmount()}"
}

private fun Long.coinAmount(): String {
    val lastHundred = this % 100
    val unit = when {
        lastHundred in 11L..14L -> "монет"
        this % 10 == 1L -> "монету"
        this % 10 in 2L..4L -> "монеты"
        else -> "монет"
    }
    return "$this $unit"
}
