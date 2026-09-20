package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.engine.BlockReason

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
    remaining == maximum - 1 -> "Немного устал"
    remaining == maximum - 2 -> "Устал"
    else -> "Сильно устал"
}

internal fun BlockReason.playerMessage(petName: String): String = when (this) {
    BlockReason.InvalidPetName -> "Введи непустое имя в одну строку."
    BlockReason.MustEat -> "$petName проголодался. Сначала нужно поесть, затем можно продолжить."
    BlockReason.MustSleep -> "$petName устал. Сил на это действие не хватает. Сначала нужно отдохнуть — оставшиеся события дождутся завтра."
    BlockReason.StaleRevision -> "Игра уже изменилась. Данные обновлены, повтори действие."
    BlockReason.EventInProgress -> "Сначала закончи текущее событие или выбери «Вернуться позже»."
    BlockReason.OnlyShortDeedsAfterSchedule -> "Сегодня остались только короткие дела. Это дело можно выполнить завтра, если его срок ещё не закончится."
    BlockReason.DeedUnavailable -> "Срок этого дела закончился или оно уже выполнено."
    BlockReason.UnfinishedEvents -> "На сегодня ещё остались события. Продолжи день."
    BlockReason.DayNotStarted -> "Сначала начни день на главном экране."
    BlockReason.DayFinished -> "День завершён. Новые дела можно выполнить после отдыха."
    BlockReason.NoNextEvent -> "Все события на сегодня закончились."
    BlockReason.PreviousLoreIncomplete -> "Сначала нужно завершить предыдущий шаг истории."
    BlockReason.StoryConditionsNotMet -> "Для этой сцены ещё нужны открытия или действия. Продолжай историю."
    BlockReason.ChapterGoalIncomplete -> "Для продолжения нужен весь комплект большой цели."
    BlockReason.GoalUnavailable -> "Эта цель пока недоступна. Выбери одну из открытых целей."
    BlockReason.GoalAlreadySelected -> "Большая цель уже выбрана."
    BlockReason.ItemAlreadyOwned -> "Эта часть комплекта уже куплена."
    is BlockReason.FoodBudgetWarning -> "Останется $remainingBalance монет, а на еду до конца недели нужно $neededForFood."
    is BlockReason.MissingItems -> "Для этого события ещё нужны предметы."
    is BlockReason.InsufficientMoney -> "Не хватает $missing монет. Можно вернуться к делам и заработать."
    BlockReason.InvalidEventAction -> "Это действие уже недоступно. Открой событие заново."
    BlockReason.MissingCarriedLore, is BlockReason.InvalidContent -> "Не удалось продолжить эту игру с текущим содержимым. Сохранение осталось на месте."
}
