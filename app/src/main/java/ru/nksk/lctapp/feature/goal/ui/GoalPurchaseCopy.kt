package ru.nksk.lctapp.feature.goal.ui

import ru.nksk.lctapp.domain.economy.BudgetSection

internal fun PurchaseConfirmation.paymentDescription(): String {
    val sources = buildList {
        if (fromSavings > 0) add("${goalPaymentCoins(fromSavings)} из копилки")
        availableParts.forEach { part ->
            val source = when (part.section) {
                BudgetSection.NEEDS -> "из денег на необходимое"
                BudgetSection.WANTS -> "из денег на желания"
                BudgetSection.SAVINGS -> "из монет, которые собирались отложить"
                BudgetSection.RESERVE -> "из запаса"
            }
            add("${goalPaymentCoins(part.amount)} $source")
        }
    }
    if (sources.isEmpty()) return "Покупка бесплатная."
    return "Для оплаты возьмём " + if (sources.size == 1) sources.single() + "."
        else sources.dropLast(1).joinToString(", ") + " и " + sources.last() + "."
}

internal fun goalPaymentCoins(amount: Long): String = "$amount " + when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> "монету"
    amount % 10 in 2..4 -> "монеты"
    else -> "монет"
}

internal fun goalMissingCoins(amount: Long): String = "$amount " + when {
    amount % 100 in 11..14 -> "монет"
    amount % 10 == 1L -> "монеты"
    else -> "монет"
}
