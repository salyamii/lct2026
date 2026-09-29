package ru.nksk.lctapp.feature.menutour

internal const val MainTourStepCount = 9
internal const val TourFinished = 11

internal data class MenuTourStep(
    val index: Int,
    val title: String,
    val body: String,
    val targets: List<String>,
    val pointerTarget: String = targets.last(),
    val expansion: String? = null,
)

internal val MenuTourSteps = listOf(
    MenuTourStep(0, "Твой спутник", "Это твой спутник! Вместе вы будете исследовать мир. Если захочешь изменить его имя, нажми сюда.", listOf("menu.pet", "menu.name")),
    MenuTourStep(1, "Монетки", "Здесь видно, сколько монет у тебя есть. Нажми на монетки, чтобы открыть бюджет и изменить распределение.", listOf("menu.coins")),
    MenuTourStep(2, "Копилка и история", "За этой стрелкой спрятаны твоя копилка и история приключения. Здесь же можно посмотреть, как распределены монеты. Список можно прокрутить.", listOf("menu.budget.arrow", "menu.budget.details"), pointerTarget = "menu.budget.arrow", expansion = "menu.budget.details"),
    MenuTourStep(3, "Твоя цель", "Наверху видно твою цель и прогресс. Нажми «Цель», чтобы узнать, что нужно собрать для приключения.", listOf("menu.goal.summary", "menu.goal")),
    MenuTourStep(4, "Снаряжение", "Здесь хранятся твои предметы и аксессуары. Можно посмотреть находки и выбрать, что наденет спутник.", listOf("menu.gear")),
    MenuTourStep(5, "Дела", "Здесь появляются предложенные тебе дела. Выполняй их, чтобы зарабатывать монеты, или заходи потренироваться.", listOf("menu.tasks")),
    MenuTourStep(6, "Карта", "Открой карту, чтобы посмотреть места и выбрать, куда отправиться.", listOf("menu.map")),
    MenuTourStep(7, "Настройки", "Здесь можно настроить звук и найти раздел для родителей.", listOf("menu.settings")),
    MenuTourStep(8, "Начать приключение", "Нажми сюда, чтобы продолжить приключение. Когда придёт время отдыхать, эта кнопка поможет закончить день.", listOf("menu.continue")),
    MenuTourStep(9, "Состояние спутника", "Здесь видно, какой сейчас день, сколько сил у спутника и поел ли он. Следи за этим перед новыми делами!", listOf("menu.status")),
    MenuTourStep(10, "Обед", "Спутнику нужно есть каждый день. Здесь можно выбрать обед: перед покупкой ты увидишь его цену и что он даст.", listOf("menu.meal")),
)

internal fun initialTourStep(savedStep: Int?, newPlayer: Boolean): Int =
    savedStep?.coerceIn(0, TourFinished) ?: if (newPlayer) 0 else TourFinished

internal fun visibleTourStep(index: Int, availableTargets: Set<String>): MenuTourStep? =
    MenuTourSteps.getOrNull(index)?.takeIf { step -> step.targets.all { it in availableTargets } }
