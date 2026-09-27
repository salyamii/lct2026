package ru.nksk.lctapp.core.ui.game

import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.domain.economy.SpendingQuote
import ru.nksk.lctapp.domain.economy.BudgetSection

/** Presentation of the same quote used by the domain when committing a payment. */
internal fun SpendingQuote.playerDescription(kind: SpendingKind): String? {
    if (blocked) return null
    if (missing > 0) {
        return if (kind == SpendingKind.WANT) {
            val price = Math.addExact(parts.sumOf { it.amount }, missing)
            "Для оплаты нужно $price ${priceCoins(price)}. Не хватает ещё $missing ${missingCoins(missing)}."
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
    val sources = parts.map { part ->
        val source = when (part.section) {
            BudgetSection.NEEDS -> "из денег на необходимое"
            BudgetSection.WANTS -> "из денег на желания"
            BudgetSection.SAVINGS -> "из монет, которые собирались отложить"
            BudgetSection.RESERVE -> "из запаса"
        }
        "${part.amount} ${paymentCoins(part.amount)} $source"
    }
    return "$prefix " + if (sources.size == 1) sources.single() + "."
        else sources.dropLast(1).joinToString(", ") + " и ещё " + sources.last() + "."
}

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
