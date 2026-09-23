package ru.nksk.lctapp.feature.economy.ui

internal const val BUDGET_STEP = 5L

/** Labels and help text for the economy screens; balances are supplied by the caller. */
internal enum class BudgetArticle(val title: String, val subtitle: String, val explanation: String) {
    NEEDS("Нужно", "Ежедневная еда и забота", "При недельном планировании нужно выделить минимум 35 монет. Между поступлениями деньги можно перераспределять свободно. Отсюда сначала оплачивается еда. Если денег не хватит, используем Запас, затем Хочу и Коплю."),
    WANTS("Хочу", "Приятные покупки", "Деньги на желания. Если их не хватит, покупка затронет Запас, затем Коплю и Нужно."),
    SAVINGS("Коплю", "Части большой цели", "Предметы цели покупаются только отсюда. Другие расходы могут затронуть накопления по своему порядку списания."),
    RESERVE("Запас", "На неожиданности и приключения", "Сюда поступает заработок из событий и мини-игр. Отсюда сначала оплачиваются события состояния, случайные и сюжетные события. При нехватке используем Хочу, затем Коплю и Нужно.");

    val minimum: Long get() = if (this == NEEDS) 35L else 0L

}

internal data class BudgetUiState(
    val needs: Long,
    val wants: Long,
    val savings: Long,
    val reserve: Long,
    val unallocated: Long,
    val weeklyIncome: Long,
    val actionsEnabled: Boolean = true,
    val minimumNeeds: Long = 35,
) {
    val canConfirm: Boolean get() = actionsEnabled && unallocated == 0L && needs >= minimumNeeds
    val total: Long get() = needs + wants + savings + reserve + unallocated
    fun amount(article: BudgetArticle): Long = when (article) {
        BudgetArticle.NEEDS -> needs
        BudgetArticle.WANTS -> wants
        BudgetArticle.SAVINGS -> savings
        BudgetArticle.RESERVE -> reserve
    }
}
