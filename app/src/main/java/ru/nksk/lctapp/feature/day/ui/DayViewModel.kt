package ru.nksk.lctapp.feature.day.ui

import ru.nksk.lctapp.domain.pet.renderPetText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.nksk.lctapp.core.ui.game.GameActionAttempt
import ru.nksk.lctapp.core.ui.game.playerDescription
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.eventCompletionMessage
import ru.nksk.lctapp.core.ui.game.restingPetArtwork
import ru.nksk.lctapp.core.ui.game.asGameActionLabel
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.game.deedDeadline
import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.core.ui.game.sceneArtwork
import ru.nksk.lctapp.core.ui.game.AdventurePetPresentation
import ru.nksk.lctapp.core.ui.game.EventSceneArtwork
import ru.nksk.lctapp.core.ui.game.eventSceneArtwork
import ru.nksk.lctapp.core.ui.game.eventSceneBackground
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition

internal data class DayOption(val id: String, val label: String, val needsFood: Boolean, val enabled: Boolean, val spending: String? = null)
internal data class StoryGameRequest(val occurrenceId: String, val choiceId: String)
internal data class MealOption(val id: String, val label: String, val enabled: Boolean, val spending: String? = null, val consequence: String? = null)
internal data class ResourcePriorityUi(val offerId: String, val title: String, val effort: String,
    val reward: Long, val comparisons: List<String>, val selected: Boolean)
internal data class DayUiState(
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
    val retryRequired: Boolean = false,
    val title: String = "", val body: String = "", val category: String = "Событие",
    val impact: String = "", val effort: String = "",
    val scene: String? = null, val character: String? = null,
    val options: List<DayOption> = emptyList(), val later: String? = null,
    val primary: String? = null, val primaryNeedsFood: Boolean = false,
    val primarySpending: String? = null,
    val showMeals: Boolean = false,
    val meals: List<MealOption> = emptyList(), val message: String? = null,
    val actionNotice: String? = null,
    val petName: String = "",
    val summary: DaySummaryUiState? = null,
    val restingPetRes: Int? = null,
    val financialContext: DecisionContext? = null,
    val practiceRequired: Boolean = false,
    val reflectionAvailable: Boolean = false,
    val resourcePriority: ResourcePriorityUi? = null,
    val layout: EventLayout = EventLayout.SCENE,
    val locationTitle: String = "",
    val purchaseArtworkRes: Int? = null,
    val pet: AdventurePetPresentation? = null,
    val eventArtwork: EventSceneArtwork? = null,
    val eventBackgroundRes: Int = R.drawable.menu_village,
    val deedDeadline: String? = null,
    val audioOccurrenceId: String? = null,
    val eventMedia: EventMedia = EventMedia(),
) {
    /** Presentation only; NPC conversations and introductions retain their clear scenery. */
    val focusesItem: Boolean get() = summary == null && when (layout) {
        EventLayout.PURCHASE -> true
        EventLayout.SCENE -> eventArtwork?.isCharacter == false
        EventLayout.INTRODUCTION -> false
    }
}

internal sealed interface DayAction {
    data object Retry : DayAction
    data object Primary : DayAction
    data class Choose(val id: String) : DayAction
    data class FinancialContextPresented(val id: String) : DayAction
    data object OpenLearning : DayAction
    data object OpenReflection : DayAction
    data class SetResourcePriority(val offerId: String, val selected: Boolean) : DayAction
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
    private val storyGames = Channel<StoryGameRequest>(Channel.BUFFERED)
    val openStoryGame = storyGames.receiveAsFlow()
    private val learning = Channel<Unit>(Channel.BUFFERED)
    val openLearning = learning.receiveAsFlow()
    private val reflections = Channel<Int>(Channel.BUFFERED)
    val openReflection = reflections.receiveAsFlow()
    private var reflectionDay: Int? = null
    private var shownFinancialContext: String? = null
    private var selectedResourcePriority: String? = null
    private var game: GameState? = null
    private var loading: Job? = null
    private var busy = false
    private var mealsShown = false
    private var message: String? = null
    private data class PendingAction(val attempt: GameActionAttempt, val action: DayAction,
        val leave: Boolean, val fed: Boolean, val openGame: Boolean)
    private var pendingAction: PendingAction? = null

    init { load() }

    private fun load() {
        if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            mutableState.value = DayUiState()
            try {
                session.prepare()
                session.observe().collect { saved ->
                    if (game != saved) {
                        if (pendingAction == null) message = null
                        if (saved?.engine?.ateToday == true) mealsShown = false
                    }
                    if (game?.engine?.revision != saved?.engine?.revision ||
                        game?.engine?.currentEvent?.id != saved?.engine?.currentEvent?.id) selectedResourcePriority = null
                    game = checkNotNull(saved)
                    val finishedDay = saved.engine?.takeIf { it.phase == DayPhase.FINISHED }?.day
                    reflectionDay = if (finishedDay != null) {
                        try {
                            finishedDay.takeIf { completedDay ->
                                session.timeMachine.availableReflections().decisions.any { it.day == completedDay }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                    } else null
                    render()
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { mutableState.value = DayUiState(loading = false, failed = true) }
        }
    }

    fun onAction(action: DayAction) {
        if (action is DayAction.FinancialContextPresented) {
            if (mutableState.value.financialContext?.presentationId == action.id) shownFinancialContext = action.id
            return
        }
        if (busy) return
        pendingAction?.let { pending ->
            if (action == DayAction.Retry || action == pending.action) submit(pending)
            return
        }
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
                execute(saved, it, action, leave = it is EngineCommand.AcknowledgeResult ||
                    (it is EngineCommand.BeginDay && !it.openFirst))
            }
            is DayAction.Choose -> {
                val miniGame = isDeed(saved)
                val command = choiceCommand(saved, action.id,
                    selectedResourcePriority.takeIf { it == mutableState.value.resourcePriority?.offerId })
                execute(saved, command, action, leave = !miniGame && command !is EngineCommand.StartStoryGame,
                    openGame = miniGame)
            }
            DayAction.OpenLearning -> viewModelScope.launch { learning.send(Unit) }
            DayAction.OpenReflection -> if (mutableState.value.reflectionAvailable) {
                mutableState.value.summary?.day?.let { day -> viewModelScope.launch { reflections.send(day) } }
            }
            is DayAction.SetResourcePriority -> {
                if (mutableState.value.resourcePriority?.offerId != action.offerId) return
                selectedResourcePriority = action.offerId.takeIf { action.selected }
                render()
            }
            is DayAction.Feed -> execute(saved, EngineCommand.Feed(action.id), action, fed = true)
            DayAction.Later -> saved.engine?.currentEvent?.let {
                execute(saved, if (isProposal(saved)) EngineCommand.DismissDeedProposal(it.id)
                    else if (it.status == EventStatus.RESULT) EngineCommand.AcknowledgeResult(it.id)
                    else EngineCommand.PauseEvent(it.id), action, leave = true)
            }
            DayAction.Retry -> Unit
            is DayAction.FinancialContextPresented -> Unit
        }
    }

    private fun execute(saved: GameState, command: EngineCommand, action: DayAction, leave: Boolean = false,
        fed: Boolean = false, openGame: Boolean = false) {
        val context = mutableState.value.financialContext?.let { displayed ->
            val presented = displayed.presentationId == shownFinancialContext
            displayed.copy(informationPresented = presented,
                complete = presented && displayed.before == financialPosition(saved) &&
                    displayed.presentationId == presentationId(saved))
        }
        val pending = PendingAction(GameActionAttempt.prepare(saved, command, context), action, leave, fed, openGame)
        pendingAction = pending
        submit(pending)
    }

    private fun submit(pending: PendingAction) {
        val saved = pending.attempt.before
        val command = pending.attempt.request.command
        val leave = pending.leave
        val fed = pending.fed
        val openGame = pending.openGame
        busy = true
        message = null
        mutableState.value = mutableState.value.copy(busy = true, retryRequired = false)
        viewModelScope.launch {
            var exitRequested = false
            try {
                when (val result = pending.attempt.submit(session)) {
                    is EngineResult.Applied -> {
                        pendingAction = null
                        selectedResourcePriority = null
                        game = result.state
                        if (fed) {
                            mealsShown = false
                            message = "${result.state.pet.name} поел. " +
                                if (primaryCommand(result.state) is EngineCommand.FinishDayFromEvent)
                                    "Теперь можно закончить день — оставшиеся события перенесём на завтра."
                                else "Можно продолжить."
                        }
                        if (openGame) {
                            val started = pending.attempt.committedState(session, result.state)?.engine?.currentEvent
                            val current = result.state.engine?.currentEvent
                            if (started != null && current?.id == started.id && current.status == EventStatus.ACTIVE) {
                                exitRequested = true
                                games.send(started.id)
                            } else message = "Действие сохранено. Состояние дела уже изменилось."
                        }
                        if (command is EngineCommand.StartStoryGame) {
                            val current = result.state.engine?.currentEvent
                            if (current?.id == command.occurrenceId && current.status == EventStatus.ACTIVE) {
                                exitRequested = true
                                storyGames.send(StoryGameRequest(command.occurrenceId, command.choiceId))
                            } else message = "Действие сохранено. Состояние дела уже изменилось."
                        }
                        if (leave && result.state.economy.planning == null) {
                            exitRequested = true
                            exits.send(if (command is EngineCommand.CompleteEvent)
                                pending.attempt.committedState(session, result.state)?.let {
                                    eventCompletionMessage(saved, it, command, session.catalog)
                                } ?: "Готово!"
                                else if (command is EngineCommand.PauseEvent && result.state.engine?.events?.any {
                                    it.id == command.occurrenceId && it.status == EventStatus.CARRIED_ACTIVE
                                } == true) "Вернёмся к этой ситуации завтра. Пока можно заняться делами." else null)
                        }
                    }
                    is EngineResult.Blocked -> {
                        pendingAction = null
                        message = result.reason.playerMessage(saved.pet.name)
                        if (result.reason == BlockReason.MustEat) mealsShown = true
                        if (result.reason is BlockReason.FinancialPracticeRequired) {
                            learning.send(Unit)
                        }
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

    private fun choiceCommand(saved: GameState, choice: String, resourcePriorityOfferId: String? = null): EngineCommand {
        val occurrence = checkNotNull(saved.engine?.currentEvent)
        val priority = session.catalog.goals.selectedGoal(saved)?.goalId
        return if (isProposal(saved)) EngineCommand.AcceptDeedProposal(occurrence.id, priorityId = priority)
        else if (occurrence.origin == EventOrigin.DEED) EngineCommand.StartDeed(checkNotNull(occurrence.deedOfferId), priorityId = priority)
        else if (choice in session.catalog.policies.getValue(occurrence.eventId).choiceGameKinds)
            EngineCommand.StartStoryGame(occurrence.id, choice, resourcePriorityOfferId)
        else EngineCommand.CompleteEvent(occurrence.id, choice, resourcePriorityOfferId = resourcePriorityOfferId)
    }

    /** A declared plan refers to a real offer and a branch where it can still be started today. */
    private fun resourcePriority(saved: GameState, choiceIds: List<String>): ResourcePriorityUi? {
        val day = saved.engine ?: return null
        val occurrence = day.currentEvent ?: return null
        val catalog = session.catalog
        val policy = catalog.policies.getValue(occurrence.eventId)
        val choices = catalog.content.choices.filter { it.id in choiceIds && it.moneyDelta <= 0 }
        if (choices.none { paid -> choices.any { other ->
                paid.moneyDelta < other.moneyDelta && policy.energyFor(paid.id) < policy.energyFor(other.id)
            } }) return null
        val outcomes = choices.mapNotNull { choice ->
            (session.engine.previewEventChoice(saved, occurrence.id, choice.id) as? EngineResult.Applied)?.state
        }
        val offer = session.engine.availableDeeds(saved).firstOrNull { candidate ->
            candidate.expiresDay == day.day && outcomes.any { after ->
                session.engine.blockReason(after, EngineCommand.StartDeed(candidate.id)) == null
            }
        } ?: return null
        val event = catalog.content.events.first { it.id == offer.eventId }
        return ResourcePriorityUi(offer.id, renderPetText(catalog.displayTitle(event), saved.pet.name).asGameUiText(),
            effortCost(catalog.policies.getValue(offer.eventId).energyCost),
            catalog.content.choices.filter { it.eventId == offer.eventId }.maxOf { it.moneyDelta },
            choices.map { choice ->
                val money = if (choice.moneyDelta == 0L) "без траты монет" else "${Math.negateExact(choice.moneyDelta)} монет"
                val action = renderPetText(catalog.displayAction(choice), saved.pet.name).substringBefore('\u00b7').trimEnd()
                "$action: $money, ${effortCost(policy.energyFor(choice.id)).lowercase()}"
            }, selectedResourcePriority == offer.id)
    }

    private fun effortCost(cost: Int) = when (cost) {
        0 -> "Не тратит силы"
        1 -> "Немного устанет"
        2 -> "Средне устанет"
        else -> "Сильно устанет"
    }

    private fun financialPosition(saved: GameState) = FinancialPosition(saved.economy.availableBalance,
        saved.economy.savingsBalance, session.catalog.mealPolicy.foodRequirement(saved))

    private fun presentationId(saved: GameState): String =
        "day:${saved.engine?.currentEvent?.id}:${saved.engine?.revision}"

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
        if (pendingAction != null) {
            mutableState.value = mutableState.value.copy(busy = false, retryRequired = true, message = message)
            return
        }
        val saved = game ?: return
        val day = saved.engine
        val occurrence = day?.currentEvent
        val catalog = session.catalog
        val event = occurrence?.let { catalog.content.events.single { e -> e.id == it.eventId } }
        val card = event?.let { catalog.cards[it.id] }
        val variant = card?.variants?.firstOrNull { catalog.storyProgress(saved).meets(it.condition) }
        val result = occurrence?.status == EventStatus.RESULT && !isProposal(saved)
        val choices = if (event != null && !result) catalog.content.choices.filter {
            it.eventId == event.id && it.id !in catalog.policies.getValue(event.id).disabledChoiceIds
        }.sortedBy { it.position } else emptyList()
        val blocked = choices.associate { it.id to session.engine.blockReason(saved, choiceCommand(saved, it.id)) }
        val primary = primaryCommand(saved)
        val primaryBlock = primary?.let { session.engine.blockReason(saved, it) }
        val restFromCard = primary is EngineCommand.FinishDayFromEvent
        val summary = session.engine.daySummary(saved)
        val presentation = card?.presentation ?: EventPresentation()
        // Presentation does not bypass gameplay guards or turn a paid/work choice into an introduction.
        val simpleStoryAction = event?.type == EventType.STORY && !result && choices.size == 1 &&
            event.moneyDeltaOnStart == 0L && choices.single().moneyDelta == 0L &&
            catalog.policies.getValue(event.id).energyFor(choices.single().id) == 0
        val layout = if (!result && !restFromCard && summary == null &&
            (presentation.layout != EventLayout.INTRODUCTION || simpleStoryAction)) presentation.layout else EventLayout.SCENE
        val purchaseCard = layout == EventLayout.PURCHASE
        val purchaseArtworkRes = eventMediaArtwork(presentation.media.artworkKey).takeIf { purchaseCard }
        val storyIntroduction = layout == EventLayout.INTRODUCTION
        val completedChoice = occurrence?.let { active -> saved.story.decisions.find { it.id == "${active.id}:decision" } }
            ?.let { decision -> catalog.content.choices.find { it.id == decision.choiceId } }
        val body = when {
            summary != null -> "${saved.pet.name} отдыхает."
            result -> if (event?.type == EventType.EARNING) "Дело выполнено. Получено ${completedChoice?.moneyDelta ?: 0} монет."
                else "Этот шаг истории завершён."
            event != null -> renderPetText(presentation.body ?: variant?.body ?: event.description, saved.pet.name).asGameUiText()
            day == null -> ""
            day.phase == DayPhase.READY_TO_END -> "Все события на сегодня закончились. До сна ещё можно выполнить короткое дело из списка «Дела»."
            else -> ""
        }
        val mealPrice = catalog.mealPolicy.basicMeal.price
        val feedingChoices = event?.let { catalog.policies.getValue(it.id).feedsPetChoiceIds }.orEmpty()
        val foodWarning = if (day?.ateToday == false && choices.any {
            it.id !in feedingChoices && it.moneyDelta < 0 && blocked[it.id] == null &&
                saved.economy.availableBalance + it.moneyDelta < mealPrice
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
        val goalSelected = catalog.goals.selectedGoal(saved) != null
        val offeredDeed = if (isDeed(saved)) day?.deeds?.find { offer ->
            offer.id == occurrence?.deedOfferId || offer.id == "${occurrence?.id}:offer"
        } else null
        val resourcePriority = if (restFromCard) null else resourcePriority(saved, choices.filter { blocked[it.id] == null }.map { it.id })
        if (resourcePriority?.offerId != selectedResourcePriority) selectedResourcePriority = null
        mutableState.value = DayUiState(
            loading = false, busy = busy, petName = saved.pet.name,
            audioOccurrenceId = occurrence?.id.takeIf { !result && summary == null },
            eventMedia = presentation.media,
            summary = summary?.toUiState(catalog, saved.pet.name, saved.economy),
            reflectionAvailable = summary != null && summary.day == reflectionDay,
            restingPetRes = summary?.let { restingPetArtwork(saved.pet) },
            resourcePriority = resourcePriority,
            layout = layout,
            locationTitle = renderPetText(presentation.locationTitle ?: card?.category.orEmpty(), saved.pet.name),
            purchaseArtworkRes = purchaseArtworkRes,
            deedDeadline = offeredDeed?.let { deedDeadline(checkNotNull(day).day, it.expiresDay) },
            pet = PetEventCondition.forPresentation(saved, session.catalog.policies).toAdventurePetPresentation(),
            eventArtwork = presentation.media.sceneArtwork(event?.let(catalog::displayTitle).orEmpty())
                ?: eventSceneArtwork(event?.id, variant?.character ?: card?.character),
            eventBackgroundRes = eventMediaArtwork(presentation.media.sceneKey)
                ?: eventSceneBackground(event?.id, presentation.media.sceneKey ?: variant?.scene ?: card?.scene),
            practiceRequired = blocked.values.any { it is BlockReason.FinancialPracticeRequired } || primaryBlock is BlockReason.FinancialPracticeRequired,
            title = when {
                summary != null -> "День ${summary.day} завершён"
                result -> "Готово!"
                event != null -> renderPetText(catalog.displayTitle(event), saved.pet.name).asGameUiText()
                primaryBlock == BlockReason.MustEat -> "Пора подкрепиться"
                primary == EngineCommand.FinishDay -> "Пора отдохнуть"
                day == null -> "Начнём приключение"
                else -> "Продолжим приключение"
            },
            body = if (event == null && summary == null && actionNotice != null) "" else body,
            category = if (summary != null) "Итоги дня" else card?.category?.let { renderPetText(it, saved.pet.name).asGameUiText() } ?: saved.pet.name,
            impact = if (result || storyIntroduction || purchaseCard) "" else if (isDeed(saved)) "Награда: до ${choices.single().moneyDelta} монет" else renderPetText(card?.impact.orEmpty(), saved.pet.name).asGameUiText(),
            effort = if (result || simpleStoryAction || !presentation.showEffort) "" else
                renderPetText(card?.effort.orEmpty(), saved.pet.name).asGameUiText().takeUnless {
                    it in setOf("Без траты сил", "Без трат сил", "Без расхода сил", "Не тратит силы", "Можно отказаться")
                }.orEmpty(),
            scene = variant?.scene ?: card?.scene, character = variant?.character ?: card?.character,
            options = (if (restFromCard) emptyList() else choices).map { DayOption(it.id, if (isDeed(saved)) {
                if (goalSelected) "Заработать на цель" else "Выполнить дело"
            } else renderPetText(catalog.displayAction(it), saved.pet.name).asGameActionLabel(),
                blocked[it.id] == BlockReason.MustEat, blocked[it.id] == null || blocked[it.id] == BlockReason.MustEat,
                if (it.moneyDelta < 0 && blocked[it.id] !is BlockReason.InsufficientMoney)
                    EconomyOperations.quote(saved.economy, Math.negateExact(it.moneyDelta),
                        SpendingKind.forEvent(checkNotNull(event).type)).playerDescription(SpendingKind.forEvent(event.type))
                else (blocked[it.id] as? BlockReason.InsufficientMoney)?.takeIf { choiceBlock !is BlockReason.InsufficientMoney }
                    ?.let { reason -> "Не хватает ${reason.missing} монет" }) },
            later = if (event != null && !result) {
                if (isDeed(saved)) "Сделать позже"
                else if (card != null) card.later?.let { renderPetText(it, saved.pet.name).asGameActionLabel() }
                else "Вернуться позже"
            } else null,
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
            meals = catalog.mealPolicy.choices(saved).map {
                val quote = EconomyOperations.quote(saved.economy, it.price, SpendingKind.FEEDING)
                MealOption(it.id, if (it.price == 0L) "Поесть бесплатно" else "Обычный обед за ${it.price} монет",
                    session.engine.blockReason(saved, EngineCommand.Feed(it.id)) == null,
                    quote.playerDescription(SpendingKind.FEEDING),
                    "После еды сегодня понадобится отдых. Утром будем немного уставшими.".takeIf { _ -> catalog.mealPolicy.effects(it.id).exhaustsCurrentEnergy })
            }, message = effectiveMessage, actionNotice = actionNotice,
        )
    }
}
