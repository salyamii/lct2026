package ru.nksk.lctapp.domain.economy

/** Real savings and current available allocations are separate payment sources. */
data class GoalPurchaseQuote(
    val fromSavings: Long,
    val fromAvailable: SpendingQuote,
) {
    init { require(fromSavings >= 0) }
    val fromAvailableAmount: Long get() = fromAvailable.parts.fold(0L) { total, part -> Math.addExact(total, part.amount) }
    val missing: Long get() = fromAvailable.missing
    val affordable: Boolean get() = fromAvailable.affordable
}
