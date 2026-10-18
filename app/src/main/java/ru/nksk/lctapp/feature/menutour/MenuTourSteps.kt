package ru.nksk.lctapp.feature.menutour

internal const val MainTourStepCount = 10
internal const val TourFinished = 12

internal data class MenuTourStep(
    val index: Int,
    val title: String,
    val body: String,
    val targets: List<String>,
    val pointerTarget: String = targets.last(),
    val expansion: String? = null,
    val cardBelowTarget: Boolean = false,
)

internal val MenuTourSteps = listOf(
    MenuTourStep(0, "Твой спутник", "Это твой спутник! Вместе вы будете исследовать мир.", listOf("menu.pet"), cardBelowTarget = true),
    MenuTourStep(1, "Имя спутника", "Здесь написано имя твоего спутника. Если захочешь изменить его, нажми на имя.", listOf("menu.name")),
    MenuTourStep(2, "Монетки", "Здесь видно, сколько монет у тебя есть. Нажми на монетки, чтобы открыть бюджет и изменить распределение.", listOf("menu.coins")),
    MenuTourStep(3, "Копилка и история", "За этой стрелкой спрятаны твоя копилка и история приключения. Здесь же можно посмотреть, как распределены монеты. Список можно прокрутить.", listOf("menu.budget.arrow", "menu.budget.details"), pointerTarget = "menu.budget.arrow", expansion = "menu.budget.details"),
    MenuTourStep(4, "Твоя цель", "На кнопке видно, сколько уже собрано. Нажми «Цель», чтобы посмотреть, что ещё нужно для приключения.", listOf("menu.goal")),
    MenuTourStep(5, "Снаряжение", "Здесь хранятся твои предметы и аксессуары. Можно посмотреть находки и выбрать, что наденет спутник.", listOf("menu.gear")),
    MenuTourStep(6, "Дела", "Здесь появляются предложенные тебе дела. Выполняй их, чтобы зарабатывать монеты, или заходи потренироваться.", listOf("menu.tasks")),
    MenuTourStep(7, "Карта", "Открой карту, чтобы посмотреть места и выбрать, куда отправиться.", listOf("menu.map")),
    MenuTourStep(8, "Настройки", "Здесь можно настроить звук и найти раздел для родителей.", listOf("menu.settings")),
    MenuTourStep(9, "Начать приключение", "Нажми сюда, чтобы продолжить приключение. Когда придёт время отдыхать, эта кнопка поможет закончить день.", listOf("menu.continue")),
    MenuTourStep(10, "Состояние спутника", "Здесь видны энергия, сытость и настроение. Нажми на показатель, чтобы узнать, что на него влияет. Проверяй их перед новыми делами!", listOf("menu.status")),
    MenuTourStep(11, "Обед", "Спутнику нужно есть каждый день. Здесь можно выбрать обед: перед покупкой ты увидишь его цену и что он даст.", listOf("menu.meal")),
)

internal fun initialTourStep(savedStep: Int?, newPlayer: Boolean): Int =
    savedStep?.coerceIn(0, TourFinished) ?: if (newPlayer) 0 else TourFinished

internal fun visibleTourStep(index: Int, availableTargets: Set<String>): MenuTourStep? =
    MenuTourSteps.getOrNull(index)?.takeIf { step -> step.targets.all { it in availableTargets } }
