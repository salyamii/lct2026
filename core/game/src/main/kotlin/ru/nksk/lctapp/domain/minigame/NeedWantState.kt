package ru.nksk.lctapp.domain.minigame

import kotlin.random.Random

/** Предмет для «Нужды или желания?»: [need] - необходимая вещь, иначе простое желание. */
data class NeedWantItem(val name: String, val need: Boolean)

/**
 * Чистое состояние «Нужда или желание?»: [ITEM_COUNT] предметов, каждый игрок относит
 * к необходимому или к желаниям. Финал после последнего предмета.
 */
data class NeedWantState(
    val items: List<NeedWantItem>,
    val current: Int = 0,
    val correctAnswers: Int = 0,
    val lastCorrect: Boolean? = null,
) {
    init {
        require(items.count { it.need } == items.size - items.count { it.need })
    }

    val item: NeedWantItem get() = items[current]
    val finished: Boolean get() = current >= items.size

    /** true - игрок отнёс предмет к необходимому. Фиксирует результат, ждёт [next]. */
    fun answer(saidNeed: Boolean): NeedWantState {
        if (finished || lastCorrect != null) return this
        val correct = item.need == saidNeed
        return copy(correctAnswers = if (correct) correctAnswers + 1 else correctAnswers, lastCorrect = correct)
    }

    fun next(): NeedWantState {
        if (finished || lastCorrect == null) return this
        return copy(current = current + 1, lastCorrect = null)
    }

    companion object {
        const val ITEM_COUNT = 8

        /** Пул мира игры: половина - необходимое для дороги и здоровья, половина - желания. */
        private val POOL = listOf(
            NeedWantItem("хлеб и сыр", need = true),
            NeedWantItem("тёплый плащ", need = true),
            NeedWantItem("лечебная мазь", need = true),
            NeedWantItem("фонарь для ночного пути", need = true),
            NeedWantItem("верёвка", need = true),
            NeedWantItem("запас воды", need = true),
            NeedWantItem("волшебный плащ со звёздами", need = false),
            NeedWantItem("украшение из ракушек", need = false),
            NeedWantItem("бумажный змей", need = false),
            NeedWantItem("сладости", need = false),
            NeedWantItem("красочный свиток-комикс", need = false),
            NeedWantItem("музыкальная шкатулка", need = false),
        )

        /** Ровно половина пула - необходимое; состав и порядок каждый раз новые. */
        fun create(random: Random = Random.Default): NeedWantState {
            val needs = POOL.filter { it.need }.shuffled(random).take(ITEM_COUNT / 2)
            val wants = POOL.filterNot { it.need }.shuffled(random).take(ITEM_COUNT / 2)
            return NeedWantState(items = (needs + wants).shuffled(random))
        }
    }
}
