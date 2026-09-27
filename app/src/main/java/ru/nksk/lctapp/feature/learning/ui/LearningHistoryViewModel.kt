package ru.nksk.lctapp.feature.learning.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.core.ui.game.BudgetHistoryUi
import ru.nksk.lctapp.core.ui.game.budgetHistoryUi
import ru.nksk.lctapp.domain.economy.BudgetRevisionReason
import ru.nksk.lctapp.domain.economy.BudgetSection
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.engine.GameSession
import ru.nksk.lctapp.domain.finance.FinancialBudgetProjection
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.AuditEntry

internal data class PeriodUi(val title: String, val body: String, val plan: String,
    val actual: List<String> = emptyList(), val comparisons: List<BudgetComparisonUi> = emptyList(), val note: String? = null)
internal data class BudgetComparisonUi(val title: String, val rows: List<String>, val note: String)
internal data class HistoryUiState(
    val loading: Boolean = true, val error: String? = null,
    val periods: List<PeriodUi> = emptyList(), val operations: List<String> = emptyList(),
    val coinMovements: BudgetHistoryUi? = null, val hasPlans: Boolean = false,
)

@HiltViewModel
internal class LearningHistoryViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val state = MutableStateFlow(HistoryUiState())
    val uiState = state.asStateFlow()
    private var observation: Job? = null
    private var active = false

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        if (value) retry() else {
            observation?.cancel()
            observation = null
        }
    }

    fun retry() {
        if (!active || observation?.isActive == true) return
        state.value = state.value.copy(loading = true, error = null)
        observation = viewModelScope.launch {
            try {
                session.prepare()
                session.observe().collect { value ->
                    val game = checkNotNull(value)
                    val history = session.history()
                    state.value = withContext(Dispatchers.Default) { historyPresentation(game, history, session.catalog) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.value = state.value.copy(loading = false, error = "Не удалось прочитать историю. Повтори попытку.") }
        }
    }
}

internal fun historyPresentation(game: GameState, shownHistory: List<AuditEntry>, catalog: GameCatalog,
    historyRowLimit: Int = 50): HistoryUiState {
    val reports = FinancialBudgetProjection.report(game, shownHistory, catalog.content).associateBy { it.periodId }
    val coinMovements = budgetHistoryUi(game, shownHistory)
    val periods = game.financial.periods.reversed().map { period ->
        val report = reports.getValue(period.id)
        PeriodUi("Период ${period.ordinal}: ${catalog.content.goals.find { it.id == period.goalId }?.title ?: "Большая цель"}",
            "Получили ${period.income} монет, потратили ${period.spentAvailable + period.spentSavings}. " +
                "В копилку положили ${period.deposited}, обратно взяли ${period.withdrawn}.", plan = "",
            actual = with(report.actual) { buildList {
                add("На необходимое потратили $needs монет")
                add("На приятные покупки потратили $wants монет")
                add("На неожиданности и другие покупки потратили $reserve монет")
                add("Пополнения копилки за вычетом снятого: $netSaved монет")
                add("Из копилки потратили $goalPurchases монет на снаряжение для цели")
                if (goalPurchasesAvailable > 0) add("На предметы цели из текущих денег потратили $goalPurchasesAvailable монет")
                if (unknownExpenses > 0) add("Назначение старых трат неизвестно: $unknownExpenses монет")
            } },
            comparisons = report.comparisons.map { comparison ->
                val revision = comparison.revision
                val reason = when (revision.reason) {
                    BudgetRevisionReason.INITIAL -> "Начальный план."
                    BudgetRevisionReason.KNOWN_NEED_OMITTED -> "Вспомнили, что ещё понадобится."
                    BudgetRevisionReason.UNEXPECTED_EXPENSE -> "Пересмотрели после неожиданной траты."
                    BudgetRevisionReason.NEW_INCOME -> "Получили новые монеты."
                    BudgetRevisionReason.CHANGED_PRIORITY -> "Решили, что сейчас важнее."
                    BudgetRevisionReason.UNSPECIFIED -> "Причина изменения не указана."
                }
                val interval = if (comparison.nextRevisionId != null) "Здесь все действия до следующего изменения плана."
                    else if (comparison.finalised) "Здесь все действия после этого плана до конца главы."
                    else "Глава ещё идёт. Здесь всё, что произошло после этого плана к текущему моменту."
                BudgetComparisonUi(if (revision.ordinal == 1) "Первоначальный план, день ${revision.day}"
                    else "План ${revision.ordinal}, день ${revision.day}",
                    BudgetSection.entries.map { section ->
                        val label = when (section) {
                            BudgetSection.NEEDS -> "Нужно"
                            BudgetSection.WANTS -> "Хочу"
                            BudgetSection.SAVINGS -> "В копилку"
                            BudgetSection.RESERVE -> "Запас"
                        }
                        val planned = revision.allocation.amount(section)
                        val actual = comparison.actual.amount(section)
                        val difference = when {
                            !comparison.complete -> "часть истории неизвестна"
                            actual > planned -> "на ${actual - planned} больше"
                            actual < planned -> "на ${planned - actual} меньше"
                            else -> "совпадает"
                        }
                        if (comparison.complete) "$label: планировали $planned, получилось $actual ($difference)"
                        else "$label: планировали $planned, в истории есть $actual ($difference)"
                    }, "$reason $interval")
            },
            note = if (!report.complete) "В старой истории не хватает подробностей. Показываем только то, что знаем точно."
                else "Чтобы сравнить накопления с планом, из пополнений вычитаем монеты, которые взяли обратно. Купленное для цели показываем отдельно.")
    }
    val operations = learningHistoryRows(shownHistory, catalog, game.pet.name, limit = historyRowLimit)
    return HistoryUiState(loading = false, periods = periods, operations = operations,
        coinMovements = coinMovements, hasPlans = game.financial.plans.isNotEmpty())
}
