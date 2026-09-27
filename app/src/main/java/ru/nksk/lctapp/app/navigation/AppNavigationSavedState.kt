package ru.nksk.lctapp.app.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import ru.nksk.lctapp.feature.economy.navigation.Economy
import ru.nksk.lctapp.feature.economy.navigation.Savings
import ru.nksk.lctapp.feature.day.navigation.Day
import ru.nksk.lctapp.feature.gear.navigation.Gear
import ru.nksk.lctapp.feature.goal.navigation.Goal
import ru.nksk.lctapp.feature.menu.navigation.MainMenu
import ru.nksk.lctapp.feature.tasks.navigation.StarPlates
import ru.nksk.lctapp.feature.tasks.navigation.PriceCheck
import ru.nksk.lctapp.feature.tasks.navigation.Telescope
import ru.nksk.lctapp.feature.tasks.navigation.Tasks
import ru.nksk.lctapp.feature.tasks.navigation.DeedGame
import ru.nksk.lctapp.feature.map.navigation.GameMap
import ru.nksk.lctapp.feature.learning.navigation.Learning
import ru.nksk.lctapp.feature.learning.navigation.SkillTraining
import ru.nksk.lctapp.feature.learning.navigation.OtherPaths
import ru.nksk.lctapp.feature.settings.navigation.Settings

/** Saves stable route IDs and can read the former Android serializer's JVM class names. */
internal val AppNavigationSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(MainMenu::class, MainMenu.serializer())
            subclass(Economy::class, Economy.serializer())
            subclass(Savings::class, Savings.serializer())
            subclass(Day::class, Day.serializer())
            subclass(Gear::class, Gear.serializer())
            subclass(Goal::class, Goal.serializer())
            subclass(Tasks::class, Tasks.serializer())
            subclass(DeedGame::class, DeedGame.serializer())
            subclass(StarPlates::class, StarPlates.serializer())
            subclass(PriceCheck::class, PriceCheck.serializer())
            subclass(Telescope::class, Telescope.serializer())
            subclass(GameMap::class, GameMap.serializer())
            subclass(Learning::class, Learning.serializer())
            subclass(SkillTraining::class, SkillTraining.serializer())
            subclass(OtherPaths::class, OtherPaths.serializer())
            subclass(Settings::class, Settings.serializer())

            // Read-only migration; new saves always use each key's @SerialName.
            defaultDeserializer { className ->
                when (className) {
                    "ru.nksk.lctapp.ui.menu.MainMenu" -> MainMenu.serializer()
                    "ru.nksk.lctapp.ui.coins.navigation.Coins" -> Economy.serializer()
                    "ru.nksk.lctapp.ui.day.navigation.Day" -> Day.serializer()
                    "ru.nksk.lctapp.ui.gear.navigation.Gear" -> Gear.serializer()
                    "ru.nksk.lctapp.ui.goal.navigation.Goal" -> Goal.serializer()
                    "ru.nksk.lctapp.ui.tasks.navigation.Tasks" -> Tasks.serializer()
                    "ru.nksk.lctapp.ui.village.navigation.Village" -> GameMap.serializer()
                    else -> null
                }
            }
        }
    }
}
