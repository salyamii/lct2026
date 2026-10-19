package ru.nksk.lctapp.domain.minigame.chapterone

/** An entrusted supply order; these prices never debit the child's own money. */
enum class TripSupply(val price: Int, val required: Boolean) {
    MAP(4, true), LANTERN(2, true), BUN(1, false), KEYCHAIN(2, false), TOY_BOAT(5, false),
}

data class TripPacking(
    val selected: Set<TripSupply> = emptySet(),
    val confirmed: Boolean = false,
) {
    init {
        require(selected.sumOf { it.price } <= BUDGET)
        require(!confirmed || ready)
    }

    val spent: Int get() = selected.sumOf { it.price }
    val reserve: Int get() = BUDGET - spent
    val essentialsPacked: Boolean get() = TripSupply.entries.filter { it.required }.all { it in selected }
    val ready: Boolean get() = essentialsPacked && reserve >= MINIMUM_RESERVE
    val completed: Boolean get() = confirmed && ready

    fun toggle(supply: TripSupply): TripPacking = when {
        confirmed -> this
        supply in selected -> copy(selected = selected - supply)
        supply.price > reserve -> this
        else -> copy(selected = selected + supply)
    }

    fun finish(): TripPacking = if (ready && !confirmed) copy(confirmed = true) else this

    companion object {
        const val BUDGET = 12
        const val MINIMUM_RESERVE = 3

        fun restore(names: List<String>, confirmed: Boolean): TripPacking {
            val selected = names.map { name -> TripSupply.entries.find { it.name == name } ?: return TripPacking() }
            if (selected.distinct().size != selected.size || selected.sumOf { it.price } > BUDGET) return TripPacking()
            val restored = TripPacking(selected.toSet())
            return if (!confirmed) restored else if (restored.ready) restored.finish() else TripPacking()
        }
    }
}
