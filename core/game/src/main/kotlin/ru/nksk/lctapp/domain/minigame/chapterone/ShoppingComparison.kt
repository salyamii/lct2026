package ru.nksk.lctapp.domain.minigame.chapterone

enum class ShoppingSide { LEFT, RIGHT }
enum class ShoppingItem { MAP, LANTERN, LENS }

data class ShoppingRound(
    val id: String,
    val item: ShoppingItem,
    val leftPrice: Int,
    val rightPrice: Int,
) {
    fun price(side: ShoppingSide): Int = if (side == ShoppingSide.LEFT) leftPrice else rightPrice
}

/** Raw, authored prices are retained alongside the child's choice for financial evidence. */
data class ShoppingAnswer(
    val roundId: String,
    val leftPrice: Int,
    val rightPrice: Int,
    val selected: ShoppingSide,
) {
    val selectedPrice: Int get() = if (selected == ShoppingSide.LEFT) leftPrice else rightPrice
    val correct: Boolean get() = selectedPrice == minOf(leftPrice, rightPrice)
    val extraCost: Int get() = selectedPrice - minOf(leftPrice, rightPrice)
}

/** A replayable observation of one shopping session, including unfinished orders. */
data class ShoppingProgress(val seriesId: String, val state: ShoppingComparison) {
    init { require(seriesId.isNotBlank()) }
}

/** Three orders with equal useful goods. A choice stays visible until the child moves on. */
data class ShoppingComparison(
    val answers: List<ShoppingAnswer> = emptyList(),
    val feedbackShown: Boolean = false,
) {
    init {
        require(answers.size <= rounds.size)
        require(!feedbackShown || answers.isNotEmpty())
        require(answers.size < rounds.size || feedbackShown)
        require(answers.withIndex().all { (index, answer) ->
            val round = rounds[index]
            answer.roundId == round.id && answer.leftPrice == round.leftPrice && answer.rightPrice == round.rightPrice
        })
    }

    val completed: Boolean get() = answers.size == rounds.size && feedbackShown
    val currentIndex: Int get() = answers.size - if (feedbackShown) 1 else 0
    val currentRound: ShoppingRound get() = rounds[currentIndex]
    val correctAnswers: Int get() = answers.count { it.correct }
    val lastAnswer: ShoppingAnswer? get() = answers.lastOrNull().takeIf { feedbackShown }

    fun choose(side: ShoppingSide): ShoppingComparison {
        if (feedbackShown || completed) return this
        val round = currentRound
        return copy(answers = answers + ShoppingAnswer(round.id, round.leftPrice, round.rightPrice, side), feedbackShown = true)
    }

    fun next(): ShoppingComparison = if (feedbackShown && !completed) copy(feedbackShown = false) else this

    companion object {
        val rounds: List<ShoppingRound> = listOf(
            ShoppingRound("same-map", ShoppingItem.MAP, 6, 4),
            ShoppingRound("same-lantern", ShoppingItem.LANTERN, 2, 5),
            ShoppingRound("same-lens", ShoppingItem.LENS, 3, 3),
        )

        fun restore(selections: List<String>, feedbackShown: Boolean): ShoppingComparison {
            if (selections.size > rounds.size || (feedbackShown && selections.isEmpty()) ||
                (selections.size == rounds.size && !feedbackShown)) return ShoppingComparison()
            var restored = ShoppingComparison()
            selections.forEachIndexed { index, name ->
                val choice = ShoppingSide.entries.find { it.name == name } ?: return ShoppingComparison()
                restored = restored.choose(choice)
                if (index < selections.lastIndex || !feedbackShown) restored = restored.next()
            }
            return restored
        }
    }
}
