package ru.nksk.lctapp.domain.minigame

import kotlinx.serialization.Serializable

/** Fixed Figma exercises. Prices here never debit the player’s wallet. */
@Serializable
sealed interface PracticeQuestion {
    val choiceCount: Int
    val correctChoice: Int
}

enum class PracticeItem {
    MAP, WATER, BUN, LANTERN, RINGS, TOY_BOAT, KEYCHAIN, CAP, BACKPACK,
    MEDICINE, BACKPACK_REPAIR, BREAD, CANDY, PLANK, CANDLES, NAILS, HAMMER,
    STAR_MAP, SCARF, TEDDY, FISHING_ROD, BAIT, BUCKET,
}

@Serializable
data class NeedWantQuestion(val item: PracticeItem, val neededForTrip: Boolean) : PracticeQuestion {
    init { require(item in setOf(PracticeItem.MAP, PracticeItem.WATER, PracticeItem.BUN,
        PracticeItem.LANTERN, PracticeItem.RINGS, PracticeItem.TOY_BOAT, PracticeItem.KEYCHAIN, PracticeItem.CAP)) }
    override val choiceCount: Int = 2
    override val correctChoice: Int get() = if (neededForTrip) 0 else 1
}

@Serializable
data class DiscountQuestion(
    val item: PracticeItem,
    val yesterdayPrice: Int,
    val crossedOutPrice: Int,
    val todayPrice: Int,
) : PracticeQuestion {
    init { require(yesterdayPrice > 0 && crossedOutPrice > 0 && todayPrice > 0) }
    override val choiceCount: Int = 2
    override val correctChoice: Int get() = if (todayPrice < yesterdayPrice) 1 else 0
    val saving: Int get() = yesterdayPrice - todayPrice
}

enum class TrapScenario { TICKET, BORROWED_LANTERN, URGENT_TICKET, DELIVERY_FEE }

@Serializable
data class FinancialOption(val price: Int, val extraCost: Int = 0, val waitDays: Int = 0) {
    init { require(price > 0 && extraCost >= 0 && waitDays >= 0) }
    val total: Long get() = price.toLong() + extraCost
}

/** Pick the least expensive option that arrives before the stated deadline. */
@Serializable
data class FinancialTrapQuestion(
    val scenario: TrapScenario,
    val deadlineDays: Int,
    val options: List<FinancialOption>,
) : PracticeQuestion {
    init {
        require(deadlineDays >= 0 && options.size == 2)
        val eligible = options.filter { it.waitDays <= deadlineDays }
        require(eligible.isNotEmpty())
        require(eligible.count { it.total == eligible.minOf(FinancialOption::total) } == 1)
    }
    override val choiceCount: Int get() = options.size
    override val correctChoice: Int get() = options.indices
        .filter { options[it].waitDays <= deadlineDays }.minBy { options[it].total }
}

enum class PurchaseGoal { WORKBENCH, OBSERVATORY, FISHING }

@Serializable
data class ExtraPurchaseQuestion(
    val goal: PurchaseGoal,
    val items: List<PracticeItem>,
    val extraItem: PracticeItem,
) : PracticeQuestion {
    init { require(items.size == 4 && items.distinct().size == 4 && extraItem in items) }
    override val choiceCount: Int get() = items.size
    override val correctChoice: Int get() = items.indexOf(extraItem)
}

sealed interface PracticeChoiceAction {
    data class Answer(val choice: Int, val round: Int) : PracticeChoiceAction
    data class Next(val round: Int) : PracticeChoiceAction
    data object Restart : PracticeChoiceAction
}

/** One answer per round. Feedback must be acknowledged before advancing. */
@Serializable
data class PracticeChoiceState<Q : PracticeQuestion>(
    val questions: List<Q>,
    val current: Int = 0,
    val answers: List<Int> = emptyList(),
) {
    init {
        require(questions.isNotEmpty() && current in 0..questions.size)
        require(answers.size in current..minOf(current + 1, questions.size))
        require(answers.withIndex().all { (index, answer) -> answer in 0 until questions[index].choiceCount })
    }
    val finished: Boolean get() = current == questions.size
    val question: Q get() = questions[minOf(current, questions.lastIndex)]
    val selectedChoice: Int? get() = answers.getOrNull(current)
    val correctAnswers: Int get() = answers.withIndex().count { (index, answer) ->
        questions[index].correctChoice == answer
    }
    val lastCorrect: Boolean? get() = selectedChoice?.let { it == question.correctChoice }

    fun reduce(action: PracticeChoiceAction): PracticeChoiceState<Q> = when (action) {
        is PracticeChoiceAction.Answer -> if (finished || selectedChoice != null ||
            action.round != current || action.choice !in 0 until question.choiceCount) this
            else copy(answers = answers + action.choice)
        is PracticeChoiceAction.Next -> if (finished || selectedChoice == null || action.round != current) this
            else copy(current = current + 1)
        PracticeChoiceAction.Restart -> PracticeChoiceState(questions)
    }
}

/** Figma examples plus authored practice variations; these are not shop prices or event assignments. */
object PracticeGames {
    fun needs() = PracticeChoiceState(listOf(
        NeedWantQuestion(PracticeItem.MAP, true),
        NeedWantQuestion(PracticeItem.TOY_BOAT, false),
        NeedWantQuestion(PracticeItem.WATER, true),
        NeedWantQuestion(PracticeItem.RINGS, false),
        NeedWantQuestion(PracticeItem.BUN, true),
        NeedWantQuestion(PracticeItem.KEYCHAIN, false),
        NeedWantQuestion(PracticeItem.LANTERN, true),
        NeedWantQuestion(PracticeItem.CAP, false),
    ))

    fun discounts() = PracticeChoiceState(listOf(
        DiscountQuestion(PracticeItem.BACKPACK, 42, 52, 34),
        DiscountQuestion(PracticeItem.LANTERN, 55, 90, 65),
        DiscountQuestion(PracticeItem.BUN, 8, 8, 5),
        DiscountQuestion(PracticeItem.CAP, 25, 40, 28),
        DiscountQuestion(PracticeItem.TOY_BOAT, 8, 12, 7),
    ))

    fun traps() = PracticeChoiceState(listOf(
        FinancialTrapQuestion(TrapScenario.TICKET, 3,
            listOf(FinancialOption(20), FinancialOption(14, waitDays = 1))),
        FinancialTrapQuestion(TrapScenario.BORROWED_LANTERN, 3,
            listOf(FinancialOption(100, 50), FinancialOption(100, waitDays = 2))),
        FinancialTrapQuestion(TrapScenario.URGENT_TICKET, 0,
            listOf(FinancialOption(14), FinancialOption(10, waitDays = 1))),
        FinancialTrapQuestion(TrapScenario.DELIVERY_FEE, 1,
            listOf(FinancialOption(40, 15), FinancialOption(48))),
    ))

    fun extraPurchases() = PracticeChoiceState(listOf(
        ExtraPurchaseQuestion(PurchaseGoal.WORKBENCH,
            listOf(PracticeItem.PLANK, PracticeItem.CANDLES, PracticeItem.NAILS, PracticeItem.HAMMER),
            PracticeItem.CANDLES),
        ExtraPurchaseQuestion(PurchaseGoal.OBSERVATORY,
            listOf(PracticeItem.LANTERN, PracticeItem.STAR_MAP, PracticeItem.SCARF, PracticeItem.TEDDY),
            PracticeItem.TEDDY),
        ExtraPurchaseQuestion(PurchaseGoal.FISHING,
            listOf(PracticeItem.FISHING_ROD, PracticeItem.BAIT, PracticeItem.BUCKET, PracticeItem.CANDLES),
            PracticeItem.CANDLES),
    ))
}
