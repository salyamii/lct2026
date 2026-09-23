package ru.nksk.lctapp.app

import ru.nksk.lctapp.R
import ru.nksk.lctapp.data.game.content.STARS_GOAL
import ru.nksk.lctapp.data.game.content.TOWER_GOAL
import ru.nksk.lctapp.data.game.content.HOME_GOAL
import ru.nksk.lctapp.feature.onboarding.ui.AdventureGoalOption

/** App-owned artwork/copy mapping; availability comes from GameSession. */
internal fun onboardingGoalOptions(): List<AdventureGoalOption> = listOf(
    AdventureGoalOption(STARS_GOAL, "Ночь наблюдений", "Открой для себя звёздное небо",
        "Впереди — звёздное небо и удивительные открытия. Подготовься к своей первой ночи наблюдений!",
        R.drawable.location_observatory),
    AdventureGoalOption(TOWER_GOAL, "Подготовка к башне", "Подготовься к исследованию башни",
        "Впереди — загадки старой башни. Собери снаряжение и подготовься к новым открытиям!",
        R.drawable.location_trail_day),
    AdventureGoalOption(HOME_GOAL, "Дом исследователя", "Создай своё место для открытий",
        "У каждого исследователя есть место, куда хочется возвращаться. Обустрой свой дом для будущих открытий!",
        R.drawable.location_workshop_day),
)
