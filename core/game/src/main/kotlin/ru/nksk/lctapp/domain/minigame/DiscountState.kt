package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/**
 * Один вопрос «Настоящей скидки»: на ярлыке зачёркнута «старая» цена [stickerOld] и написана
 * новая [priceNow], но [fairPrice] - настоящая прежняя цена этого товара (её показывает, например,
 * другая лавка). Скидка настоящая, только если новая цена ниже настоящей прежней.
 */
data class DiscountQuestion(
    val item: String,
    val stickerOld: Int,
    val priceNow: Int,
    val fairPrice: Int,
) {
    init {
        require(priceNow > 0 && fairPrice > 0 && stickerOld > priceNow)
    }

    val realDiscount: Boolean get() = priceNow < fairPrice
}

/**
 * Чистое состояние «Настоящая скидка»: [ROUNDS] ярлыков. Игрок решает, стала ли покупка
 * действительно дешевле. Финал после последнего ответа.
 */
data class DiscountState(
    val questions: List<DiscountQuestion>,
    val current: Int = 0,
    val correctAnswers: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    val question: DiscountQuestion get() = questions[current]
    val finished: Boolean get() = current >= questions.size

    /** true - игрок верит, что скидка настоящая. Фиксирует результат, ждёт [next]. */
    fun answer(believedReal: Boolean): DiscountState {
        if (finished || lastCorrect != null) return this
        val correct = question.realDiscount == believedReal
        return copy(correctAnswers = if (correct) correctAnswers + 1 else correctAnswers, lastCorrect = correct)
    }

    fun next(): DiscountState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, lastCorrect = null)
    }

    companion object {
        const val ROUNDS = 5
        private val ITEMS = listOf(
            "фонарь", "верёвка", "термос", "плащ", "карта", "лопатка", "кружка", "шест",
        )

        /** Половина ярлыков честные, половина - с завышенной «старой» ценой; порядок случайный. */
        fun create(random: Random = Random.Default): DiscountState {
            val questions = List(ROUNDS) { index ->
                val fair = random.nextInt(12, 80)
                val real = index < ROUNDS / 2
                val now = if (real) fair - random.nextInt(3, 10) else fair + random.nextInt(1, 8)
                val item = ITEMS[random.nextInt(ITEMS.size)]
                DiscountQuestion(item = item, stickerOld = now + random.nextInt(5, 20),
                    priceNow = now, fairPrice = fair)
            }
            return DiscountState(questions.shuffled(random))
        }
    }
}
