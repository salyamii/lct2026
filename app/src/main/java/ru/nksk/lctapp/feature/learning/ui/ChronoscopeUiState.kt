package ru.nksk.lctapp.feature.learning.ui

import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.game.asGameActionLabel
import ru.nksk.lctapp.core.ui.game.AdventurePetPresentation
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.engine.DayPhase
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.displayAction
import ru.nksk.lctapp.domain.engine.displayOutcome
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry
import ru.nksk.lctapp.domain.pet.renderPetText
import ru.nksk.lctapp.domain.pet.PetCosmetics
import ru.nksk.lctapp.domain.timemachine.TimeMachineBranch
import ru.nksk.lctapp.domain.timemachine.TimeMachineDecision
import ru.nksk.lctapp.domain.timemachine.TimeMachineResult
import ru.nksk.lctapp.domain.timemachine.TimeMachineStatus

/** Presentation only: none of these steps writes a simulated snapshot to the real game. */
internal enum class ChronoscopeStep {
    INTRO, MOMENTS, MEMORY, ALTERNATIVES, CONSEQUENCES, COMPARISON, QUIZ, RETRY, EXPLANATION, PRESENT, UNAVAILABLE,
}

internal data class ChronoscopeMemory(
    val decision: TimeMachineDecision,
    val runId: String,
    val before: GameState,
    val originalAction: String,
    val knownNeeds: Long?,
    val sceneKey: String?,
    val originalChoice: String,
)

internal data class ChronoscopePath(
    val state: GameState,
    val petPresentation: AdventurePetPresentation,
    val money: List<Pair<String, Long>>,
    val gainedItems: List<ChronoscopeItemOutcome>,
    val usedItems: List<ChronoscopeItemOutcome>,
)

internal data class ChronoscopeItemOutcome(val itemId: String, val title: String, val count: Int)
internal data class ChronoscopeAmountDifference(val label: String, val original: Long, val alternative: Long)
internal data class ChronoscopeDifferences(
    val original: List<String>,
    val alternative: List<String>,
    val money: List<ChronoscopeAmountDifference>,
)

internal fun chronoscopeMemory(decision: TimeMachineDecision, entry: AuditEntry, catalog: GameCatalog): ChronoscopeMemory? {
    val before = entry.before ?: return null
    val eventId = before.engine?.currentEvent?.eventId
    val selectedId = when (val command = entry.request?.command) {
        is EngineCommand.Choose -> command.choiceId
        is EngineCommand.CompleteEvent -> command.choiceId
        is EngineCommand.CompleteStoryGame -> command.choiceId
        else -> null
    }
    val choice = catalog.content.choices.find { it.id == selectedId && it.eventId == eventId }
    fun text(value: String) = renderPetText(value, before.pet.name).asGameUiText()
    val choiceLabel = choice?.let { renderPetText(catalog.displayAction(it), before.pet.name).asGameActionLabel() }
    return ChronoscopeMemory(
        decision = decision,
        runId = entry.runId,
        before = before,
        originalAction = choice?.let(catalog::displayOutcome)?.let(::text)
            ?: choiceLabel?.let { "Выбрали «$it»." }
            ?: learningHistoryRows(listOf(entry), catalog, before.pet.name, includeDay = false)
                .firstOrNull() ?: "Выбрали этот поступок.",
        knownNeeds = (entry.context ?: entry.request?.context)?.before?.knownNeeds,
        sceneKey = eventId?.let { catalog.cards[it]?.scene },
        originalChoice = choiceLabel ?: "Прежний поступок",
    )
}

internal fun chronoscopePath(branch: TimeMachineBranch, initial: GameState, catalog: GameCatalog): ChronoscopePath {
    val oldIds = initial.ownedItems.map { it.id }.toSet()
    val newIds = branch.state.ownedItems.map { it.id }.toSet()
    val gained = branch.state.ownedItems.filter { it.id !in oldIds }
    // Preview the accessory from this decision, not unrelated purchases later in the day.
    val eventId = initial.engine?.currentEvent?.eventId
    val choiceIds = catalog.content.choices.filter { it.eventId == eventId }.map { it.id }.toSet()
    val accessories = catalog.content.choiceItemEffects
        .filter { it.choiceId in choiceIds && it.operation == ItemOperation.ADD }
        .mapNotNull { effect -> PetCosmetics.forItem(effect.itemId)?.let { effect.itemId to it.lookId } }
        .toMap()
    val acquiredLook = gained.firstNotNullOfOrNull { accessories[it.itemId] }
    val petPresentation = if (accessories.isEmpty()) branch.state.pet.toAdventurePetPresentation()
        else branch.state.pet.copy(selectedLookId = acquiredLook ?: branch.state.pet.selectedLookId)
            .toAdventurePetPresentation(showReaction = false)
    fun items(values: List<ru.nksk.lctapp.domain.game.OwnedItem>): List<ChronoscopeItemOutcome> =
        values.groupingBy { it.itemId }.eachCount().map { (itemId, count) ->
            ChronoscopeItemOutcome(itemId,
                renderPetText(catalog.content.items.find { it.id == itemId }?.name ?: "Предмет", initial.pet.name).asGameUiText(), count)
        }
    return ChronoscopePath(branch.state,
        petPresentation = petPresentation,
        money = listOf("Получено" to branch.income, "Потрачено" to branch.spent,
            "Отложено" to branch.deposited, "Взято из копилки" to branch.withdrawn),
        gainedItems = items(gained),
        usedItems = items(initial.ownedItems.filter { it.id !in newIds }),
    )
}

/** Compare item identity/counts and actual state fields, never the wording of a rendered sentence. */
internal fun chronoscopeDifferences(original: ChronoscopePath, alternative: ChronoscopePath,
    showLedgerTotal: Boolean = false): ChronoscopeDifferences {
    val originalLines = mutableListOf<String>()
    val alternativeLines = mutableListOf<String>()
    fun compareItems(first: List<ChronoscopeItemOutcome>, second: List<ChronoscopeItemOutcome>,
        present: String, absent: String) {
        val firstById = first.associateBy { it.itemId }
        val secondById = second.associateBy { it.itemId }
        (firstById.keys + secondById.keys).forEach { itemId ->
            val left = firstById[itemId]
            val right = secondById[itemId]
            if ((left?.count ?: 0) == (right?.count ?: 0)) return@forEach
            val title = checkNotNull(left ?: right).title
            fun line(value: ChronoscopeItemOutcome?) = if (value == null) "$absent: $title"
                else "$present: $title" + if (value.count > 1) " × ${value.count}" else ""
            originalLines += line(left)
            alternativeLines += line(right)
        }
    }
    compareItems(original.gainedItems, alternative.gainedItems, "Получили", "Не получили")
    compareItems(original.usedItems, alternative.usedItems, "Использовали", "Сохранили")
    val before = original.state.engine
    val after = alternative.state.engine
    if (before != null && after != null) {
        val name = original.state.pet.name.ifBlank { "Лисёнок" }
        if (before.ateToday != after.ateToday) {
            fun meal(ate: Boolean) = if (ate) "$name поел" else "$name ещё не ел в этот день"
            originalLines += meal(before.ateToday)
            alternativeLines += meal(after.ateToday)
        }
        if (before.energy != after.energy) {
            fun effort(own: Int, other: Int) = when {
                own == 0 -> "Силы закончились — нужен отдых"
                other == 0 -> "Силы ещё остались"
                own > other -> "Устал меньше"
                else -> "Устал больше"
            }
            originalLines += effort(before.energy, after.energy)
            alternativeLines += effort(after.energy, before.energy)
        }
    }
    val money = buildList {
        fun addChanged(label: String, first: Long, second: Long, keepEqual: Boolean = false) {
            if (first != second || keepEqual) add(ChronoscopeAmountDifference(label, first, second))
        }
        addChanged("Осталось монет", original.state.economy.availableBalance, alternative.state.economy.availableBalance)
        addChanged("В копилке", original.state.economy.savingsBalance, alternative.state.economy.savingsBalance)
        val alternativeMoney = alternative.money.toMap()
        original.money.forEach { (label, amount) ->
            val otherAmount = alternativeMoney[label] ?: 0L
            val meaningfulSpending = label == "Потрачено" &&
                (showLedgerTotal || amount > 0L && (originalLines.isNotEmpty() || alternativeLines.isNotEmpty()))
            addChanged(label, amount, otherAmount, meaningfulSpending)
        }
    }
    return ChronoscopeDifferences(originalLines, alternativeLines, money)
}

/** Never turn a missing/invalid replay into a convincing pair of balances or an invented quiz. */
internal fun TimeMachineResult.hasComparablePaths(targetSequence: Long): Boolean {
    val reached = reachedSequence ?: return false
    val requested = requestedSequence ?: return false
    return status in setOf(TimeMachineStatus.COMPLETE, TimeMachineStatus.DIVERGED) && baseline != null && alternative != null &&
        simulationId != null && reached in targetSequence..requested
}

internal fun chronoscopeHorizon(result: TimeMachineResult, day: Int?): String {
    val shownDay = day ?: result.baseline?.state?.engine?.day ?: 1
    return when {
        result.status == TimeMachineStatus.DIVERGED -> "День $shownDay. Посмотрим, что успело бы произойти."
        result.baseline?.state?.engine?.phase == DayPhase.FINISHED -> "Что изменилось бы к концу $shownDay-го дня"
        else -> "День $shownDay. Что изменилось бы к этому моменту?"
    }
}

/** A partial result names the reached moment without presenting a later blocked action as completed. */
internal fun chronoscopeBoundary(result: TimeMachineResult, history: List<AuditEntry>, catalog: GameCatalog): String? {
    val target = history.find { it.id == result.request.entryId } ?: return null
    val reached = result.reachedSequence ?: return null
    if (reached < target.sequence) return null
    val afterTarget = history.filter { it.runId == target.runId && it.sequence > target.sequence && it.sequence <= reached }
    val action = learningHistoryRows(afterTarget, catalog, target.before?.pet?.name.orEmpty(), includeDay = false).firstOrNull()
    return action?.let { "Здесь останавливаемся: $it" }
}

/** Each amount stays next to the choice it belongs to; no separate table to mentally match. */
internal fun chronoscopeMoneyLines(differences: ChronoscopeDifferences, alternative: Boolean): List<String> =
    differences.money.map { row ->
        val amount = if (alternative) row.alternative else row.original
        val label = when (row.label) {
            "Осталось монет" -> "Монеты с собой"
            "Потрачено" -> "Потратили за это время"
            "Получено" -> "Получили за это время"
            "Отложено" -> "Положили в копилку"
            else -> row.label
        }
        "$label: $amount"
    }

internal fun chronoscopeBack(step: ChronoscopeStep): ChronoscopeStep? = when (step) {
    ChronoscopeStep.INTRO, ChronoscopeStep.MOMENTS, ChronoscopeStep.PRESENT -> null
    ChronoscopeStep.UNAVAILABLE, ChronoscopeStep.MEMORY, ChronoscopeStep.ALTERNATIVES -> ChronoscopeStep.MOMENTS
    ChronoscopeStep.CONSEQUENCES, ChronoscopeStep.COMPARISON -> ChronoscopeStep.ALTERNATIVES
    ChronoscopeStep.QUIZ, ChronoscopeStep.RETRY, ChronoscopeStep.EXPLANATION -> ChronoscopeStep.COMPARISON
}
