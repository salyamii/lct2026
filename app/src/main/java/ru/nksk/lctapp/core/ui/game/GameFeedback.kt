package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.engine.BlockReason
import ru.nksk.lctapp.domain.engine.EngineCommand
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.displayOutcome
import ru.nksk.lctapp.domain.engine.displayTitle
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.pet.renderPetText

/** Explain the outcome actually committed, including the selected alternative and real costs. */
internal fun eventCompletionMessage(before: GameState, after: GameState,
    command: EngineCommand.CompleteEvent, catalog: GameCatalog): String {
    val occurrence = before.engine?.currentEvent ?: return "Решение сохранено."
    val event = catalog.content.events.first { it.id == occurrence.eventId }
    val choice = catalog.content.choices.first { it.id == command.choiceId }
    val summary = catalog.displayOutcome(choice) ?: "Завершили: ${catalog.displayTitle(event)}"
    val parts = mutableListOf(renderPetText(summary, after.pet.name).trimEnd('.') + ".")
    val spent = before.economy.balance - after.economy.balance
    if (spent > 0) parts += "Потратили ${paymentCoinAmount(spent)}."
    else if (spent < 0) parts += "Получили ${paymentCoinAmount(-spent)}."
    val effort = (before.engine?.energy ?: 0) - (after.engine?.energy ?: 0)
    val equipped = after.ownedItems.any { item -> before.ownedItems.none { it.id == item.id } &&
        ru.nksk.lctapp.domain.pet.PetCosmetics.forItem(item.itemId) != null }
    when {
        equipped -> parts += "Обновку можно надеть в «Снаряжении»."
        after.engine?.energy == 0 -> parts += "${after.pet.name} без сил — пора отдохнуть."
        effort > 0 -> parts += "${after.pet.name} ${energyDescription(after.engine!!.energy, catalog.rules.fullEnergy).lowercase()}."
        after.engine?.ateToday == true && before.engine?.ateToday == false -> parts += "${after.pet.name} поел."
        catalog.policies[event.id]?.scheduling?.blocksStoryUntilResolved == true -> parts += "Можно продолжить историю."
    }
    return parts.joinToString(" ")
}

internal fun deedCompletionMessage(reward: Long): String {
    val coins = when {
        reward % 100 in 11L..14L -> "монет"
        reward % 10 == 1L -> "монета"
        reward % 10 in 2L..4L -> "монеты"
        else -> "монет"
    }
    return "Дело выполнено! Награда: $reward $coins"
}

/** Presentation only: the saved effort budget and action guards retain their exact values. */
internal fun energyDescription(remaining: Int, maximum: Int): String = when {
    remaining <= 0 -> "Без сил"
    remaining >= maximum -> "Полон сил"
    remaining >= maximum - 2 && remaining > 1 -> "Немного устал"
    remaining > 1 -> "Устал"
    else -> "Сильно устал"
}

internal fun BlockReason.playerMessage(petName: String): String = when (this) {
    BlockReason.BudgetPlanningRequired -> "Сначала реши, на что пойдут монеты, и подтверди бюджет."
    is BlockReason.FinancialPracticeRequired -> "Всё готово! Ответим на вопросы о наших решениях — и продолжим историю."
    BlockReason.SavingsWithdrawalConfirmationRequired -> "Чтобы взять монеты из копилки, сначала подтверди решение."
    BlockReason.InvalidPetName -> "Напиши имя спутника в одну строку."
    BlockReason.MustEat -> "$petName проголодался. Сначала нужно поесть, затем можно продолжить."
    BlockReason.MustSleep -> "$petName устал. Сил на это действие не хватает. Сначала нужно отдохнуть — оставшиеся события дождутся завтра."
    BlockReason.StaleRevision -> "Игра уже изменилась. Данные обновлены, повтори действие."
    BlockReason.EventInProgress -> "Сначала закончи текущее событие или выбери «Вернуться позже»."
    BlockReason.OnlyShortDeedsAfterSchedule -> "Сегодня можно заняться только короткими делами. К этому делу вернёмся завтра, если ещё успеваем по сроку."
    BlockReason.DeedUnavailable -> "Срок этого дела закончился или оно уже выполнено."
    BlockReason.UnfinishedEvents -> "На сегодня ещё остались события. Продолжи день."
    BlockReason.DayNotStarted -> "Сначала начни день на главном экране."
    BlockReason.DayFinished -> "День завершён. Новые дела можно выполнить после отдыха."
    BlockReason.NoNextEvent -> "Все события на сегодня закончились."
    BlockReason.PreviousLoreIncomplete -> "Сначала нужно завершить предыдущий шаг истории."
    BlockReason.StoryConditionsNotMet -> "Мы ещё не всё сделали для этого шага. Продолжай историю."
    BlockReason.ChapterGoalIncomplete -> "Чтобы продолжить историю, собери всё снаряжение для цели."
    BlockReason.GoalUnavailable -> "Эта цель пока недоступна. Выбери одну из открытых целей."
    BlockReason.GoalAlreadySelected -> "Большая цель уже выбрана."
    BlockReason.ItemAlreadyOwned -> "Эта часть комплекта уже куплена."
    is BlockReason.FoodBudgetWarning -> "Останется ${coinAmount(remainingBalance)}, а на еду до конца недели нужно $neededForFood."
    is BlockReason.MissingItems -> "Для этого события ещё нужны предметы."
    is BlockReason.InsufficientMoney -> "Не хватает ${missingCoinAmount(missing)}. Можно вернуться к делам и заработать."
    BlockReason.InvalidEventAction -> "Это действие уже недоступно. Открой событие заново."
    BlockReason.MissingCarriedLore, is BlockReason.InvalidContent -> "Не удалось продолжить эту игру с текущим содержимым. Сохранение осталось на месте."
}
