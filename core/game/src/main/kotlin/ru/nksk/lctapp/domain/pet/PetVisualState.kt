package ru.nksk.lctapp.domain.pet

/**
 * The approved state catalog. Lower [priority] values rank higher.
 * Priority is metadata; it does not block explicit updates or create hidden states.
 * Every state, including HAPPY and UPSET, lasts until an explicit event updates it.
 */
enum class PetVisualState(val priority: Int) {
    NEEDS_HELP(priority = 1),
    HUNGRY(priority = 2),
    TIRED(priority = 3),
    WORRIED(priority = 4),
    THINKING(priority = 5),
    UPSET(priority = 6),
    HAPPY(priority = 7),
    NORMAL(priority = 8),
}
