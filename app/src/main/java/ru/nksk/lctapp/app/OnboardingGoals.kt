package ru.nksk.lctapp.app

import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.game.goalItemArtwork
import ru.nksk.lctapp.feature.onboarding.ui.AdventureGoalOption

/** First chapter purchase targets. IDs and availability are validated against the catalog. */
internal fun onboardingGoalOptions(): List<AdventureGoalOption> = listOf(
    AdventureGoalOption("stargazing-star-map-v1", "Карта звёзд", "24 монеты\nНайди знакомые созвездия",
        "Начнём с карты звёзд. Откладывай монеты в копилку, следи за прогрессом и собери всё нужное для Ночи наблюдений.",
        R.drawable.location_observatory_stage, itemImage = goalItemArtwork("stargazing-star-map-v1")),
    AdventureGoalOption("stargazing-tripod-v1", "Штатив", "36 монет\nУстойчивая опора для телескопа",
        "Первой целью станет штатив. Откладывай монеты в копилку, а после покупки выбери следующую цель.",
        R.drawable.location_observatory_stage, itemImage = goalItemArtwork("stargazing-tripod-v1")),
    AdventureGoalOption("stargazing-telescope-v1", "Телескоп", "90 монет\nРассмотри далёкие звёзды",
        "Начнём копить на телескоп! Впереди Ночь наблюдений: для неё понадобятся и остальные части комплекта.",
        R.drawable.location_observatory_stage, itemImage = goalItemArtwork("stargazing-telescope-v1")),
    AdventureGoalOption("stargazing-trip-v1", "Поездка", "30 монет\nОтправляйся на Ночь наблюдений",
        "Первой целью станет поездка. Накопи на неё, затем продолжай собирать снаряжение для наблюдений.",
        R.drawable.location_observatory_stage, itemImage = goalItemArtwork("stargazing-trip-v1")),
)
