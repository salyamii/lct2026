package ru.nksk.lctapp.feature.economy.ui

import ru.nksk.lctapp.domain.economy.BudgetPlanningReason
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.pet.PetState
import ru.nksk.lctapp.core.ui.game.missingCoinAmount
import ru.nksk.lctapp.core.ui.game.coinAmount
import ru.nksk.lctapp.core.ui.game.FinancialAdviceUi

internal const val BUDGET_STEP = 5L

/** Labels and help text for the economy screens; balances are supplied by the caller. */
internal enum class BudgetArticle(val title: String, val explanation: String) {
    NEEDS("Нужно", "На еду и заботу о герое."),
    WANTS("Хочу", "На угощения, игрушки и другие радости."),
    SAVINGS("В копилку", "На большую цель. Деньги отложатся, когда подтвердишь бюджет."),
    RESERVE("Запас", "На ремонт, лечение и другие неожиданные траты.");

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
    val availableBalance: Long = needs + wants + savings + reserve + unallocated,
    val savingsBalance: Long = 0,
    val knownNeeds: Long = 0,
    val title: String = "Наш бюджет",
    val transfersEnabled: Boolean = false,
    val note: String? = null,
    val busy: Boolean = false,
    val isEditing: Boolean = true,
    val advice: FinancialAdviceUi? = null,
    val focusedArticle: BudgetArticle? = null,
    val adviceScope: String = "budget",
    val planningId: String? = null,
) {
    val canConfirm: Boolean get() = actionsEnabled && !busy && unallocated == 0L && (!isEditing || needs >= minimumNeeds)
    val total: Long get() = needs + wants + savings + reserve + unallocated
    val savingsTransfer: Long get() = if (isEditing) savings else 0
    val availableAfterConfirmation: Long get() = availableBalance - savingsTransfer
    val savingsAfterConfirmation: Long get() = Math.addExact(savingsBalance, savingsTransfer)
    /** `needs` is the displayed draft allocation; `knownNeeds` remains the full food requirement. */
    val foodShortfall: Long get() = (knownNeeds - needs).coerceAtLeast(0)
    val foodAdvice: String get() = if (foodShortfall > 0)
        "На еду до следующей недели не хватает ещё ${missingCoinAmount(foodShortfall)}."
    else "На еду до следующей недели хватает."
    val foodReminderText: String? get() = if (foodShortfall > 0)
        "На еду до следующей недели нужно ${coinAmount(knownNeeds)}. Добавь в «Нужно» ещё ${missingCoinAmount(foodShortfall)}."
    else null
    val adviser: FinancialAdviceUi get() {
        val text = when {
            focusedArticle == BudgetArticle.WANTS && wants == 0L ->
                "На приятные покупки можно оставить немного монет или пока копить на цель."
            focusedArticle == BudgetArticle.WANTS ->
                "Больше радостей сейчас или ближе большая цель? Реши, сколько потратить, а сколько отложить."
            focusedArticle == BudgetArticle.SAVINGS && savingsTransfer > 0 ->
                "После подтверждения эти монеты перейдут в копилку на большую цель."
            focusedArticle == BudgetArticle.SAVINGS ->
                "В копилке деньги хранятся отдельно от обычных покупок и приближают большую цель."
            focusedArticle == BudgetArticle.RESERVE && reserve == 0L ->
                "Запас выручит при ремонте или лечении. Пока он пуст."
            focusedArticle == BudgetArticle.RESERVE ->
                "Запас — на ремонт и лечение. Остаток можно отложить в копилку в конце недели."
            focusedArticle == BudgetArticle.NEEDS ->
                "Монеты из «Нужно» идут на еду. Другие покупки их не заберут."
            focusedArticle == null && advice != null -> advice.text
            knownNeeds == 0L ->
                "Можно оставить монеты на будущие обеды или распределить между другими планами."
            needs > knownNeeds ->
                "Лишнее можно выделить на цель, радости и неожиданные траты."
            else -> "Сначала отложи на необходимое, затем реши, сколько оставить на радости, цель и запас."
        }
        return FinancialAdviceUi("$adviceScope:${focusedArticle?.name ?: "overview"}:${text.hashCode()}", text)
    }
    fun amount(article: BudgetArticle): Long = when (article) {
        BudgetArticle.NEEDS -> needs
        BudgetArticle.WANTS -> wants
        BudgetArticle.SAVINGS -> savings
        BudgetArticle.RESERVE -> reserve
    }
}

/** Presentation retained while confirmation saves and the outgoing screen animates away. */
internal data class BudgetScreenState(
    val budget: BudgetUiState,
    val pet: PetState?,
    val revisionReason: BudgetRevisionReason?,
    val historyAvailable: Boolean,
    val contextId: String?,
)

internal fun EconomyUiState.budgetScreenState(): BudgetScreenState {
    budgetConfirmation?.let { return it }
    val economy = checkNotNull(economy)
    val planning = economy.planning
    val plan = economy.displayPlan
    return BudgetScreenState(
        budget = BudgetUiState(plan.needs, plan.wants, plan.savings, plan.reserve,
            EconomyOperations.allocationRemaining(economy), planning?.income ?: 0,
            actionsEnabled = planEditingEnabled,
            minimumNeeds = EconomyOperations.minimumNeeds(economy, knownNeeds),
            availableBalance = economy.availableBalance, savingsBalance = economy.savingsBalance,
            knownNeeds = knownNeeds, transfersEnabled = planning == null && !saving,
            title = when (planning?.reason) {
                BudgetPlanningReason.INITIAL -> "Первый план"
                BudgetPlanningReason.WEEKLY -> "План на неделю"
                BudgetPlanningReason.MIGRATION -> "Распределим остаток"
                BudgetPlanningReason.MANUAL, null -> "Наш бюджет"
            },
            busy = saving,
            isEditing = planning != null, advice = budgetAdvice,
            focusedArticle = focusedBudgetArticle, adviceScope = budgetAdviceScope,
            planningId = planning?.id),
        pet = pet,
        revisionReason = revisionReason.takeIf { planning == null || planning.reason == BudgetPlanningReason.MANUAL },
        historyAvailable = planning == null,
        contextId = confirmationContextId,
    )
}
