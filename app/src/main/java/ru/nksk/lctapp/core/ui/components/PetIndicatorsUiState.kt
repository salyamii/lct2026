package ru.nksk.lctapp.core.ui.components

/** Read-only presentation. Hunger and emotion are categories, not invented percentage meters. */
data class PetIndicatorsUiState(
    val energy: Int?,
    val maxEnergy: Int,
    val energyLabel: String,
    val energyExplanation: String,
    val satiety: PetSatietyUiState,
    val mood: PetMoodUiState,
    val demoMode: Boolean = false,
)

enum class PetSatietyUiState(val label: String, val explanation: String) {
    NOT_EATEN("Пора поесть", "Сегодня герой ещё не ел."),
    HUNGRY("Голоден", "Поешь, чтобы продолжить дела."),
    FED("Сыт", "Еды хватит до следующего дня."),
}

enum class PetMoodUiState(val label: String, val explanation: String) {
    CALM("Спокоен", "Сейчас всё спокойно."),
    HAPPY("Радуется", "Последнее событие порадовало героя."),
    UPSET("Огорчён", "Последнее событие огорчило героя."),
    THINKING("Думает", "Герой ждёт твоего решения."),
    WORRIED("Волнуется", "Герой переживает из-за события."),
    NEEDS_CARE("Нужна помощь", "Помоги герою в текущем событии."),
    HUNGRY("Хочет есть", "Покорми героя."),
    TIRED("Нужен отдых", "Сон восстановит силы."),
}

enum class PetIndicatorKind { ENERGY, SATIETY, MOOD }

/** A consequence of a completed action; opening its card or mini-game does not apply it. */
data class PetIndicatorEffectUiState(
    val kind: PetIndicatorKind,
    val label: String,
    val explanation: String? = null,
)
