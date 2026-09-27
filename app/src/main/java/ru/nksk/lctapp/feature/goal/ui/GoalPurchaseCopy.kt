package ru.nksk.lctapp.feature.goal.ui

import ru.nksk.lctapp.core.ui.game.missingCoinAmount
import ru.nksk.lctapp.core.ui.game.paymentCoinAmount
import ru.nksk.lctapp.core.ui.game.paymentSourcesDescription

internal fun PurchaseConfirmation.paymentDescription(): String =
    paymentSourcesDescription(fromSavings, availableParts) ?: "Покупка бесплатная."

internal fun goalPaymentCoins(amount: Long): String = paymentCoinAmount(amount)
internal fun goalMissingCoins(amount: Long): String = missingCoinAmount(amount)
