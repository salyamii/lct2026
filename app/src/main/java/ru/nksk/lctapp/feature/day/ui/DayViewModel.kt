package ru.nksk.lctapp.feature.day.ui

import ru.nksk.lctapp.domain.pet.renderPetText

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
import ru.nksk.lctapp.core.ui.game.playerDescription
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.energyDescription
import ru.nksk.lctapp.core.ui.game.restingPetArtwork
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState

internal data class DayOption(val id: String, val label: String, val needsFood: Boolean, val enabled: Boolean, val spending: String? = null)
internal data class MealOption(val id: String, val label: String, val enabled: Boolean, val spending: String? = null)
internal data class DayUiState(
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
    val title: String = "", val body: String = "", val category: String = "Событие",
    val status: String = "", val impact: String = "", val effort: String = "", val footer: String = "",
    val scene: String? = null, val character: String? = null,
    val options: List<DayOption> = emptyList(), val later: String? = null,
    val primary: String? = null, val primaryNeedsFood: Boolean = false,
    val primarySpending: String? = null,
    val showMeals: Boolean = false,
    val meals: List<MealOption> = emptyList(), val message: String? = null,
    val actionNotice: String? = null,
    val weeklyReminder: String? = null,
    val petName: String = "",
    val summary: DaySummaryUiState? = null,
    val restingPetRes: Int? = null,
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
                        if (fed) {
                            mealsShown = false
                            message = "${result.state.pet.name} поел. " +
                                if (primaryCommand(result.state) is EngineCommand.FinishDayFromEvent)
                                    "Теперь можно закончить день — оставшиеся события перенесём на завтра."
                                else "Можно продолжить."
                        }
                        if (openGame) {
                            exitRequested = true
                            games.send(checkNotNull(result.state.engine?.currentEvent).id)
                        }
                        if (leave && result.state.economy.planning == null) {
                            exitRequested = true
                            exits.send(if (command is EngineCommand.CompleteEvent) "Событие выполнено!" else null)
                        }
                    }
                    is EngineResult.Blocked -> {
                        message = result.reason.playerMessage(saved.pet.name)
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
        if (occurrence?.status == EventStatus.RESULT && !isProposal(saved)) return EngineCommand.AcknowledgeResult(occurrence.id)
        if (occurrence != null) {
            val rest = EngineCommand.FinishDayFromEvent(occurrence.id)
            val blocked = session.engine.blockReason(saved, rest)
            if (blocked == null || blocked == BlockReason.MustEat) return rest
        }
        return session.advanceCommand(saved)
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
        val variant = card?.variants?.firstOrNull { catalog.storyProgress(saved).meets(it.condition) }
        val result = occurrence?.status == EventStatus.RESULT && !isProposal(saved)
        val choices = if (event != null && !result) catalog.content.choices.filter { it.eventId == event.id }.sortedBy { it.position } else emptyList()
        val blocked = choices.associate { it.id to session.engine.blockReason(saved, choiceCommand(saved, it.id)) }
        val primary = primaryCommand(saved)
        val primaryBlock = primary?.let { session.engine.blockReason(saved, it) }
        val restFromCard = primary is EngineCommand.FinishDayFromEvent
        val summary = session.engine.daySummary(saved)
        val completedChoice = occurrence?.let { active -> saved.story.decisions.find { it.id == "${active.id}:decision" } }
            ?.let { decision -> catalog.content.choices.find { it.id == decision.choiceId } }
        val body = when {
            summary != null -> "${saved.pet.name} отдыхает."
            result -> if (event?.type == EventType.EARNING) "Дело выполнено. Получено ${completedChoice?.moneyDelta ?: 0} монет. Сейчас ${saved.pet.name} ${energyDescription(checkNotNull(day).energy, catalog.rules.fullEnergy).lowercase()}."
                else "Этот шаг истории завершён."
            event != null -> renderPetText(variant?.body ?: event.description, saved.pet.name)
            day == null -> "${saved.pet.name} начинает неделю со 100 монетами. Еда стоит 5 монет в день: на неделю нужно запланировать минимум 35. Перед началом игры распредели монеты по статьям."
            day.phase == DayPhase.READY_TO_END -> "Все события на сегодня закончились. До сна ещё можно выполнить короткое дело из списка «Дела»."
            day.energy == 0 -> "${saved.pet.name} устал. Оставшийся план перенесётся на завтра." +
                if (day.ateToday) " Можно отдохнуть." else " Перед сном нужно поесть."
            else -> "${saved.pet.name} готов продолжить день."
        }
        val mealPrice = catalog.meals.first { it.price > 0 }.price
        val foodWarning = if (day?.ateToday == false && choices.any {
            it.moneyDelta < 0 && blocked[it.id] == null && saved.economy.balance + it.moneyDelta < mealPrice
        }) "После этой траты на обычный обед не хватит. ${saved.pet.name} ещё не поел сегодня." else null
        // Derive the explanation from the same guards that replace the button. Keep it separate
        // from transient feedback so a save error or feeding message cannot hide the current need.
        val actionNotice = when {
            restFromCard || (primary == EngineCommand.FinishDay && (day?.energy == 0 ||
                session.engine.blockReason(saved, EngineCommand.OpenNextEvent) == BlockReason.MustSleep)) ->
                if (primaryBlock == BlockReason.MustEat) "${saved.pet.name} устал. Перед отдыхом нужно поесть, затем можно закончить день. Оставшиеся события перенесём на завтра."
                else BlockReason.MustSleep.playerMessage(saved.pet.name)
            primaryBlock == BlockReason.MustEat || blocked.values.any { it == BlockReason.MustEat } ->
                BlockReason.MustEat.playerMessage(saved.pet.name)
            else -> null
        }
        val choiceBlock = blocked.values.takeIf { reasons -> reasons.none { it == null } }
            ?.firstOrNull { it != null && it != BlockReason.MustEat }
        val effectiveMessage = message?.takeUnless { it == actionNotice }
            ?: if (actionNotice == null) choiceBlock?.playerMessage(saved.pet.name)
                ?: foodWarning ?: primaryBlock?.playerMessage(saved.pet.name) else null
        mutableState.value = DayUiState(
            loading = false, busy = busy, petName = saved.pet.name,
            summary = summary?.toUiState(catalog, saved.pet.name),
            restingPetRes = summary?.let { restingPetArtwork(saved.pet) },
            title = when { summary != null -> "День ${summary.day} завершён"; result -> "Готово!"; event != null -> renderPetText(event.title, saved.pet.name); day != null -> "День ${day.day}"; else -> "Новый день" },
            body = body, category = if (summary != null) "Итоги дня" else card?.category?.let { renderPetText(it, saved.pet.name) } ?: saved.pet.name,
            status = if (summary != null) "" else "${day?.let { "День ${it.day} · ${energyDescription(it.energy, catalog.rules.fullEnergy)} · " }.orEmpty()}${saved.economy.balance} монет",
            impact = if (result) "" else if (isDeed(saved)) "Награда: до ${choices.single().moneyDelta} монет в «Запас»" else renderPetText(card?.impact.orEmpty(), saved.pet.name),
            effort = if (result) "" else renderPetText(card?.effort.orEmpty(), saved.pet.name),
            footer = renderPetText(card?.footer.orEmpty(), saved.pet.name), scene = variant?.scene ?: card?.scene, character = variant?.character ?: card?.character,
            options = (if (restFromCard) emptyList() else choices).map { DayOption(it.id, if (isDeed(saved)) "Выполнить дело" else renderPetText(it.text, saved.pet.name),
                blocked[it.id] == BlockReason.MustEat, blocked[it.id] == null || blocked[it.id] == BlockReason.MustEat,
                if (it.moneyDelta < 0) EconomyOperations.quote(saved.economy, Math.negateExact(it.moneyDelta),
                    SpendingKind.forEvent(checkNotNull(event).type)).playerDescription(SpendingKind.forEvent(event.type)) else null) },
            later = if (event != null && !result) { if (card != null) card.later?.let { renderPetText(it, saved.pet.name) } else "Вернуться позже" } else null,
            primary = primary?.let { when {
                primaryBlock == BlockReason.MustEat -> "Покормить"
                result -> "Вернуться"
                summary != null -> "Начать день ${summary.day.toLong() + 1}"
                day == null -> "Начать день 1"
                it == EngineCommand.FinishDay || restFromCard -> "Закончить день"
                else -> "Продолжить день"
            } }, primaryNeedsFood = primaryBlock == BlockReason.MustEat,
            primarySpending = if (occurrence == null && primary != null && primaryBlock != BlockReason.MustEat)
                session.previewAdvanceSpending(saved)?.let { it.quote.playerDescription(it.kind) } else null,
            showMeals = mealsShown,
            meals = catalog.meals.filter { it.price > 0 || saved.economy.balance < catalog.meals.first().price }.map {
                val quote = EconomyOperations.quote(saved.economy, it.price, SpendingKind.FEEDING)
                MealOption(it.id, if (it.price == 0L) "Бесплатная столовая · завтра меньше сил" else "Обычный обед · ${it.price} монет",
                    session.engine.blockReason(saved, EngineCommand.Feed(it.id)) == null,
                    quote.playerDescription(SpendingKind.FEEDING))
            }, message = effectiveMessage, actionNotice = actionNotice,
            weeklyReminder = day?.takeIf { (it.day - 1) % 7 == 0 && it.steps <= 1 && summary == null }?.let {
                val mealPrice = catalog.meals.first { meal -> meal.price > 0 }.price
                "На неделю — ${catalog.rules.weeklyIncome} монет. На ежедневную еду запланируй минимум ${mealPrice * 7}. Заработок поступает в «Запас». Учитывай и будущие поездки."
            },
        )
    }
}
