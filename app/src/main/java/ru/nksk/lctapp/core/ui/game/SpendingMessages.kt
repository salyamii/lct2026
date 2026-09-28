package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.economy.SpendingQuote
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.economy.SpendPart

/** Presentation of the same quote used by the domain when committing a payment. */
internal fun SpendingQuote.playerDescription(kind: SpendingKind): String? {
    if (blocked) return null
    if (missing > 0) {
        return if (kind == SpendingKind.WANT) {
            val price = Math.addExact(parts.sumOf { it.amount }, missing)
            "Цена — ${coinAmount(price)}. Не хватает ещё $missing."
        } else "Не хватает $missing ${missingCoins(missing)}. Деньги не будут списаны."
    }
    if (parts.isEmpty()) return null
    if (kind == SpendingKind.EARNING) return null
    val amount = parts.sumOf { it.amount }
    return if (kind == SpendingKind.GOAL) "Возьмём из копилки $amount ${paymentCoins(amount)}."
    else availableSourcesDescription(prefix = "Возьмём для оплаты")
}

internal fun SpendingQuote.availableSourcesDescription(prefix: String = "Возьмём"): String? {
    if (blocked || missing > 0 || parts.isEmpty()) return null
    return paymentSourcesDescription(0, parts, prefix, "и ещё")
}

/** Common wording for an actual payment split; no allocation or affordability rules live here. */
internal fun paymentSourcesDescription(fromSavings: Long, parts: List<SpendPart>,
    prefix: String = "Для оплаты возьмём", conjunction: String = "и"): String? {
    val sources = buildList {
        if (fromSavings > 0) add("${paymentCoinAmount(fromSavings)} из копилки")
        parts.filter { it.amount > 0 }.forEach { part ->
            val source = when (part.section) {
                BudgetSection.NEEDS -> "из денег на необходимое"
                BudgetSection.WANTS -> "из денег на желания"
                BudgetSection.SAVINGS -> "из суммы, которую собирались отложить"
                BudgetSection.RESERVE -> "из запаса"
            }
            val amount = if (isEmpty()) paymentCoinAmount(part.amount) else part.amount.toString()
            add("$amount $source")
        }
    }
    if (sources.isEmpty()) return null
    return "$prefix " + if (sources.size == 1) sources.single() + "."
        else sources.dropLast(1).joinToString(", ") + " $conjunction " + sources.last() + "."
}

internal fun paymentCoinAmount(amount: Long): String = "$amount ${paymentCoins(amount)}"
internal fun missingCoinAmount(amount: Long): String = "$amount ${missingCoins(amount)}"
internal fun coinAmount(amount: Long): String = "$amount ${priceCoins(amount)}"

private fun paymentCoins(amount: Long): String = when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> "монету"
    amount % 10 in 2..4 -> "монеты"
    else -> "монет"
}

private fun priceCoins(amount: Long): String = when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> "монета"
    amount % 10 in 2..4 -> "монеты"
    else -> "монет"
}

private fun missingCoins(amount: Long): String = when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> "монеты"
    amount % 10 in 2..4 -> "монет"
    else -> "монет"
}
