package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.pet.renderPetText

internal fun deedDeadline(currentDay: Int, expiresDay: Int): String = when (expiresDay - currentDay) {
    0 -> "Можно выполнить сегодня"
    1 -> "Можно выполнить сегодня или завтра"
    else -> "Можно выполнить до конца $expiresDay-го дня"
}

private val decorativeSeparator = Regex("\\s*\u00b7\\s*")
private val legacyRouteMapName = Regex("\\b([Кк]арт(?:а|у|ы|е|ой|ою)) подходов\\b")
private val pricedAction = Regex("^(.+?)\\s*\u00b7\\s*(\\+?)(\\d+)(?:\\s+монет[аы]?)?$")
private val unnamedEffort = Regex("(^|[,;:.!?]\\s*)((?:немного |средне |заметно |сильно )?устанет|не тратит силы)(?=[.!?,;:]|$)",
    RegexOption.IGNORE_CASE)
private val manualEffortPrefix = Regex("^Самому:\\s*", RegexOption.IGNORE_CASE)

/** Name the hero in standalone effort clauses, leaving already named clauses unchanged. */
internal fun String.asPetEffortText(petName: String): String {
    val template = asGameUiText().trim().replace(manualEffortPrefix, "Если сделать самим, ")
        .replace("средне устанет", "заметно устанет", ignoreCase = true)
        .replace(unnamedEffort) { match ->
            "${match.groupValues[1]}{petName} ${match.groupValues[2].lowercase()}"
        }
    return renderPetText(template, petName.trim().ifBlank { "Герой" })
}

/** Adapt immutable catalog copy for display without rewriting content or historical facts. */
internal fun String.asGameUiText(separator: String = ", "): String =
    replace(decorativeSeparator, separator).replace(legacyRouteMapName, "$1 походов")

internal fun String.asGameActionLabel(): String {
    val match = pricedAction.matchEntire(trim()) ?: return asGameUiText()
    val (action, reward, amountText) = match.destructured
    val amount = amountText.toLongOrNull() ?: return asGameUiText()
    val coins = "$amount " + when {
        amount % 100 in 11..14 -> "монет"
        amount % 10 == 1L -> "монету"
        amount % 10 in 2..4 -> "монеты"
        else -> "монет"
    }
    return if (reward.isNotEmpty()) "$action и получить $coins" else when (action) {
        "Оплатить" -> "Заплатить $coins"
        "Оплатить очистку" -> "Очистить за $coins"
        else -> "$action за $coins"
    }
}
