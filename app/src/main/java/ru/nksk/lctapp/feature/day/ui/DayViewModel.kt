package ru.nksk.lctapp.feature.day.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.energyDescription
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

internal data class DayOption(val id: String, val label: String, val needsFood: Boolean, val enabled: Boolean)
internal data class MealOption(val id: String, val label: String, val enabled: Boolean)
internal data class DayUiState(
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
    val title: String = "", val body: String = "", val category: String = "Событие",
    val status: String = "", val impact: String = "", val effort: String = "", val footer: String = "",
    val scene: String? = null, val character: String? = null,
    val options: List<DayOption> = emptyList(), val later: String? = null,
    val primary: String? = null, val primaryNeedsFood: Boolean = false,
    val showMeals: Boolean = false,
    val meals: List<MealOption> = emptyList(), val message: String? = null,
    val weeklyReminder: String? = null,
)

internal sealed interface DayAction {
    data object Retry : DayAction
    data object Primary : DayAction
    data class Choose(val id: String) : DayAction
    data object Later : DayAction
    data object ShowMeals : DayAction
    data object CloseMeals : DayAction
    data class Feed(val id: String) : DayAction
}

@HiltViewModel
internal class DayViewModel @Inject constructor(private val session: GameSession) : ViewModel() {
    private val mutableState = MutableStateFlow(DayUiState())
    val uiState = mutableState.asStateFlow()
    private val exits = Channel<String?>(Channel.BUFFERED)
    val exit = exits.receiveAsFlow()
    private val games = Channel<String>(Channel.BUFFERED)
    val openGame = games.receiveAsFlow()
    private var game: GameState? = null
    private var loading: Job? = null
    private var busy = false
    private var mealsShown = false
    private var message: String? = null

    init { load() }

    private fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            mutableState.value = DayUiState()
            try {
                session.prepare()
                session.observe().collect { saved ->
                    if (game != saved) {
                        message = null
                        if (saved?.engine?.ateToday == true) mealsShown = false
                    }
                    game = checkNotNull(saved)
                    render()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = DayUiState(loading = false, failed = true) }
        }
    }

    fun onAction(action: DayAction) {
        if (busy) return
        if (action == DayAction.Retry) { load(); return }
        val saved = game ?: return
        when (action) {
            DayAction.ShowMeals -> {
                if (uiState.value.primaryNeedsFood || uiState.value.options.any { it.needsFood }) {
                    mealsShown = true; message = null; render()
                }
            }
            DayAction.CloseMeals -> { mealsShown = false; render() }
            DayAction.Primary -> primaryCommand(saved)?.let {
                execute(saved, it, leave = it is EngineCommand.AcknowledgeResult ||
                    (it is EngineCommand.BeginDay && !it.openFirst))
            }
            is DayAction.Choose -> {
                val miniGame = isDeed(saved)
                execute(saved, choiceCommand(saved, action.id), leave = !miniGame, openGame = miniGame)
            }
            is DayAction.Feed -> execute(saved, EngineCommand.Feed(action.id), fed = true)
            DayAction.Later -> saved.engine?.currentEvent?.let {
                execute(saved, if (isProposal(saved)) EngineCommand.DismissDeedProposal(it.id)
                    else if (it.status == EventStatus.RESULT) EngineCommand.AcknowledgeResult(it.id)
                    else EngineCommand.PauseEvent(it.id), leave = true)
            }
            DayAction.Retry -> Unit
        }
    }

    private fun execute(saved: GameState, command: EngineCommand, leave: Boolean = false,
        fed: Boolean = false, openGame: Boolean = false) {
        busy = true
        message = null
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            var exitRequested = false
            try {
                when (val result = session.dispatch(EngineRequest(UUID.randomUUID().toString(), saved.engine?.revision, command))) {
                    is EngineResult.Applied -> {
                        game = result.state
                        if (fed) { mealsShown = false; message = "Рыжик поел. Можно продолжить." }
                        if (openGame) {
                            exitRequested = true
                            games.send(checkNotNull(result.state.engine?.currentEvent).id)
                        }
                        if (leave) {
                            exitRequested = true
                            exits.send(if (command is EngineCommand.CompleteEvent) "Событие выполнено!" else null)
                        }
                    }
                    is EngineResult.Blocked -> {
                        message = result.reason.playerMessage()
                        if (result.reason == BlockReason.MustEat) mealsShown = true
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { message = "Не удалось сохранить действие. Попробуй ещё раз." }
            finally {
                if (!exitRequested) {
                    busy = false
                    render()
                }
            }
        }
    }

    private fun isProposal(saved: GameState): Boolean = saved.engine?.currentEvent?.let { occurrence ->
        occurrence.status == EventStatus.RESULT && occurrence.origin == EventOrigin.SCHEDULE &&
            session.catalog.content.events.single { it.id == occurrence.eventId }.type == EventType.EARNING
    } == true

    private fun isDeed(saved: GameState): Boolean = saved.engine?.currentEvent?.let { occurrence ->
        session.catalog.content.events.single { it.id == occurrence.eventId }.type == EventType.EARNING &&
            (isProposal(saved) || occurrence.status == EventStatus.ACTIVE)
    } == true

    private fun choiceCommand(saved: GameState, choice: String): EngineCommand {
        val occurrence = checkNotNull(saved.engine?.currentEvent)
        return if (isProposal(saved)) EngineCommand.AcceptDeedProposal(occurrence.id)
        else if (occurrence.origin == EventOrigin.DEED) EngineCommand.StartDeed(checkNotNull(occurrence.deedOfferId))
        else EngineCommand.CompleteEvent(occurrence.id, choice)
    }

    private fun primaryCommand(saved: GameState): EngineCommand? {
        val occurrence = saved.engine?.currentEvent
        return if (occurrence?.status == EventStatus.RESULT && !isProposal(saved)) EngineCommand.AcknowledgeResult(occurrence.id)
        else session.advanceCommand(saved)
    }

    private fun render() {
        // Observation can receive the saved pause/result acknowledgement before navigation exits.
        // Keep the outgoing card visible until removal; failed commands unlock and render again.
        if (busy) return
        val saved = game ?: return
        val day = saved.engine
        val occurrence = day?.currentEvent
        val catalog = session.catalog
        val event = occurrence?.let { catalog.content.events.single { e -> e.id == it.eventId } }
        val card = event?.let { catalog.cards[it.id] }
        val result = occurrence?.status == EventStatus.RESULT && !isProposal(saved)
        val choices = if (event != null && !result) catalog.content.choices.filter { it.eventId == event.id }.sortedBy { it.position } else emptyList()
        val blocked = choices.associate { it.id to session.engine.blockReason(saved, choiceCommand(saved, it.id)) }
        val primary = primaryCommand(saved)
        val primaryBlock = primary?.let { session.engine.blockReason(saved, it) }
        val summary = session.engine.daySummary(saved)
        val completedChoice = occurrence?.let { active -> saved.story.decisions.find { it.id == "${active.id}:decision" } }
            ?.let { decision -> catalog.content.choices.find { it.id == decision.choiceId } }
        val body = when {
            summary != null -> buildString {
                append("Баланс: ${summary.openingBalance} → ${summary.closingBalance} монет\n")
                append("За день: ${summary.closingBalance - summary.openingBalance} монет · Шагов: ${summary.steps}\n")
                append(if (summary.completedLoreEventIds.isEmpty()) "Новых шагов истории сегодня нет." else
                    "История: " + summary.completedLoreEventIds.joinToString { id -> catalog.content.events.single { it.id == id }.title })
                val carried = day!!.events.count { it.status == EventStatus.CARRIED || it.status == EventStatus.CARRIED_ACTIVE }
                if (carried > 0) append("\nНа завтра перенесено событий: $carried.")
            }
            result -> if (event?.type == EventType.EARNING) "Дело выполнено. Получено ${completedChoice?.moneyDelta ?: 0} монет. Сейчас Рыжик ${energyDescription(checkNotNull(day).energy, catalog.rules.fullEnergy).lowercase()}."
                else "Этот шаг истории завершён."
            event != null -> event.description
            day == null -> "В начале недели у Рыжика 100 монет. Еда стоит 5 монет в день: на неделю нужно запланировать минимум 35. Деньги остаются общими — план не блокирует покупки."
            day.phase == DayPhase.READY_TO_END -> "Все события на сегодня закончились. До сна ещё можно выполнить короткое дело из списка «Дела»."
            day.energy == 0 -> "Рыжик устал. Оставшийся план перенесётся на завтра." +
                if (day.ateToday) " Можно отдохнуть." else " Перед сном нужно поесть."
            else -> "Рыжик готов продолжить день."
        }
        val mealPrice = catalog.meals.first { it.price > 0 }.price
        val foodWarning = if (day?.ateToday == false && choices.any {
            it.moneyDelta < 0 && blocked[it.id] == null && saved.economy.balance + it.moneyDelta < mealPrice
        }) "После этой траты на обычный обед не хватит. Рыжику ещё нужно поесть сегодня." else null
        val effectiveMessage = message ?: blocked.values.firstOrNull { it != null && it != BlockReason.MustEat }?.playerMessage()
            ?: foodWarning
            ?: primaryBlock?.playerMessage()
        mutableState.value = DayUiState(
            loading = false, busy = busy,
            title = when { summary != null -> "День ${summary.day} завершён"; result -> "Готово!"; event != null -> event.title; day != null -> "День ${day.day}"; else -> "Новый день" },
            body = body, category = if (summary != null) "Итоги дня" else card?.category ?: "Рыжик",
            status = "${day?.let { "День ${it.day} · ${energyDescription(it.energy, catalog.rules.fullEnergy)} · " }.orEmpty()}${saved.economy.balance} монет",
            impact = if (result) "" else if (isDeed(saved)) "Награда: до ${choices.single().moneyDelta} монет" else card?.impact.orEmpty(),
            effort = if (result) "" else card?.effort.orEmpty(),
            footer = card?.footer.orEmpty(), scene = card?.scene, character = card?.character,
            options = choices.map { DayOption(it.id, if (isDeed(saved)) "Выполнить дело" else it.text,
                blocked[it.id] == BlockReason.MustEat, blocked[it.id] == null || blocked[it.id] == BlockReason.MustEat) },
            later = if (event != null && !result) { if (card != null) card.later else "Вернуться позже" } else null,
            primary = primary?.let { when {
                primaryBlock == BlockReason.MustEat -> "Покормить"
                result -> "Вернуться"
                summary != null || day == null -> "Начать день"
                it == EngineCommand.FinishDay -> "Закончить день"
                else -> "Продолжить день"
            } }, primaryNeedsFood = primaryBlock == BlockReason.MustEat,
            showMeals = mealsShown,
            meals = catalog.meals.filter { it.price > 0 || saved.economy.balance < catalog.meals.first().price }.map {
                MealOption(it.id, if (it.price == 0L) "Бесплатная столовая · завтра меньше сил" else "Обычный обед · ${it.price} монет", saved.economy.balance >= it.price)
            }, message = effectiveMessage,
            weeklyReminder = day?.takeIf { (it.day - 1) % 7 == 0 && it.steps <= 1 && summary == null }?.let {
                val mealPrice = catalog.meals.first { meal -> meal.price > 0 }.price
                "На неделю — ${catalog.rules.weeklyIncome} монет. На ежедневную еду запланируй минимум ${mealPrice * 7}. Деньги остаются общими, поэтому учитывай и будущие поездки."
            },
        )
    }
}
