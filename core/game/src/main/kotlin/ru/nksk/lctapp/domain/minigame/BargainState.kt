package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** Одно предложение торговца: количество товара и полная цена за всё количество. */
data class BargainOffer(val count: Int, val totalPrice: Int) {
    init {
        require(count >= 1 && totalPrice > 0 && totalPrice % count == 0)
    }

    /** Цена за одну штуку; итог всегда делится нацело, чтобы сравнение было однозначным. */
    val unitPrice: Int get() = totalPrice / count
}

/** Один вопрос «Выгодно или нет?»: два предложения одинакового товара, цена за штуку всегда разная. */
data class BargainQuestion(val left: BargainOffer, val right: BargainOffer) {
    init {
        require(left.unitPrice != right.unitPrice)
    }

    val leftIsBetter: Boolean get() = left.unitPrice < right.unitPrice
}

/**
 * Чистое состояние «Выгодно или нет?»: [ROUNDS] пар предложений, верный ответ -
 * предложение с меньшей ценой за штуку. Финал после последнего ответа.
 */
data class BargainState(
    val questions: List<BargainQuestion>,
    val current: Int = 0,
    val correctAnswers: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    val question: BargainQuestion get() = questions[current]
    val finished: Boolean get() = current >= questions.size

    /** Ответ игрока: true - выбрано левое предложение. Фиксирует результат, ждёт [next]. */
    fun answer(pickedLeft: Boolean): BargainState {
        if (finished || lastCorrect != null) return this
        val correct = question.leftIsBetter == pickedLeft
        return copy(correctAnswers = if (correct) correctAnswers + 1 else correctAnswers, lastCorrect = correct)
    }

    fun next(): BargainState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, lastCorrect = null)
    }

    companion object {
        const val ROUNDS = 5

        /** Цены за штуку заметно различаются, количества разные, итог всегда делится на количество. */
        fun create(random: Random = Random.Default): BargainState = BargainState(List(ROUNDS) {
            val leftUnit = random.nextInt(6, 22)
            var rightUnit = random.nextInt(6, 22)
            while (kotlin.math.abs(leftUnit - rightUnit) < 3) rightUnit = random.nextInt(6, 22)
            fun offer(unit: Int): BargainOffer {
                val count = random.nextInt(2, 6)
                return BargainOffer(count = count, totalPrice = unit * count)
            }
            BargainQuestion(offer(leftUnit), offer(rightUnit))
        })
    }
}
