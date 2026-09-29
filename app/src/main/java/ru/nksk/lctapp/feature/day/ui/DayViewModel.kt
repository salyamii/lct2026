package ru.nksk.lctapp.feature.day.ui

import ru.nksk.lctapp.domain.pet.renderPetText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Qualifier
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.core.ui.game.GameActionAttempt
import ru.nksk.lctapp.core.ui.game.mealChoices
import ru.nksk.lctapp.core.ui.components.MealChoiceUiState
import ru.nksk.lctapp.core.ui.game.playerDescription
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.economy.SpendingKind
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.eventCompletionMessage
import ru.nksk.lctapp.core.ui.game.restingPetArtwork
import ru.nksk.lctapp.core.ui.game.asGameActionLabel
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.game.asPetEffortText
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
import ru.nksk.lctapp.domain.history.CampaignRestartRequest
import ru.nksk.lctapp.domain.history.CampaignRestartConflictException

internal data class DayOption(val id: String, val label: String, val needsFood: Boolean, val enabled: Boolean, val spending: String? = null)
internal data class StoryGameRequest(val occurrenceId: String, val choiceId: String)
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
    val meals: List<MealChoiceUiState> = emptyList(), val message: String? = null,
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class DayComputationDispatcher

@HiltViewModel
internal class DayViewModel @Inject constructor(private val session: GameSession,
    @param:DayComputationDispatcher private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
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
    private val restarts = Channel<Unit>(Channel.BUFFERED)
    val restarted = restarts.receiveAsFlow()
    private var pendingRestart: CampaignRestartRequest? = null
    private var reflectionDay: Int? = null
    private var reflectionLoading: Job? = null
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
    private var screenActive = true
    private data class Presentation(val game: GameState, val demoMode: Boolean, val state: DayUiState,
        val primary: EngineCommand?, val includesMeals: Boolean = false, val campaignComplete: Boolean = false)
    private var presentation: Presentation? = null
    private var projection: Job? = null
    private var projectionGame: GameState? = null
    private var projectionMode = false
    private var projectionIncludesMeals = false

    init { load() }

    fun setActive(active: Boolean) {
        if (screenActive == active) return
        screenActive = active
        if (active) load() else {
            loading?.cancel(); loading = null
            reflectionLoading?.cancel()
            projection?.cancel()
        }
    }

    private fun load() {
        if (!screenActive || loading?.isActive == true) return
        reflectionLoading?.cancel()
        loading = viewModelScope.launch {
            if (game == null) mutableState.value = DayUiState()
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
                    reflectionLoading?.cancel()
                    reflectionDay = null
                    render()
                    if (finishedDay != null) reflectionLoading = viewModelScope.launch {
                        val available = try { session.timeMachine.hasReflection(finishedDay) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { false }
                        // A new day/save may arrive while this read is running. Never update its UI.
                        if (screenActive && game === saved) {
                            reflectionDay = finishedDay.takeIf { available }
                            mutableState.value = mutableState.value.copy(reflectionAvailable = available)
                        }
                    }
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
        if (busy || !screenActive || projection?.isActive == true) return
        if (pendingRestart != null && (action == DayAction.Retry || action == DayAction.Primary)) {
            restartCompletedCampaign(); return
        }
        pendingAction?.let { pending ->
            if (action == DayAction.Retry || action == pending.action) submit(pending)
            return
        }
        if (action == DayAction.Retry) {
            if (presentation?.takeIf { it.game === game }?.campaignComplete == true) restartCompletedCampaign() else load()
            return
        }
        val saved = game ?: return
        if (action == DayAction.Primary && presentation?.takeIf { it.game === saved }?.campaignComplete == true) {
            restartCompletedCampaign(); return
        }
        when (action) {
            DayAction.ShowMeals -> {
                if (uiState.value.primaryNeedsFood || uiState.value.options.any { it.needsFood }) {
                    mealsShown = true; message = null; render()
                }
            }
            DayAction.CloseMeals -> { mealsShown = false; render() }
            DayAction.Primary -> presentation?.takeIf { it.game === saved && it.demoMode == session.demoModeEnabled }?.primary?.let {
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
        val pending = PendingAction(GameActionAttempt.prepare(saved, command, context,
            demoMode = presentation?.demoMode ?: session.demoModeEnabled), action, leave, fed, openGame)
        pendingAction = pending
        submit(pending)
    }

    private fun submit(pending: PendingAction) {
        projection?.cancel()
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
                        if (command is EngineCommand.CompleteEvent && saved.engine?.currentEvent?.eventId ==
                            session.catalog.storyCampaign?.acts?.lastOrNull()?.finaleId &&
                            withContext(computationDispatcher) { session.canRestartCampaign(result.state) }) {
                            prepareCampaignRestart()
                            exitRequested = true
                            return@launch
                        }
                        if (fed) {
                            mealsShown = false
                            message = "${result.state.pet.name} поел. " +
                                if (withContext(computationDispatcher) { primaryCommand(result.state) } is EngineCommand.FinishDayFromEvent)
                                    "Теперь можно закончить день - оставшиеся события перенесём на завтра."
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
                        if (leave && result.state.economy.planning != null) {
                            // App-level routing will open the new budget. Keep this committed
                            // card frozen until then instead of showing another event for a frame.
                            exitRequested = true
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
            } catch (_: CampaignRestartConflictException) {
                pendingRestart = null
                message = "Приключение изменилось. Попробуй вернуться к началу ещё раз."
                load()
            } catch (_: Exception) { message = if (game?.let(session::canRestartCampaign) == true)
                "История завершена. Не удалось подтвердить возвращение - попробуй ещё раз."
                else "Не удалось сохранить действие. Попробуй ещё раз." }
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
    private fun resourcePriority(saved: GameState, choiceIds: List<String>, demoMode: Boolean): ResourcePriorityUi? {
        val day = saved.engine ?: return null
        val occurrence = day.currentEvent ?: return null
        val catalog = session.catalog
        val policy = catalog.policies.getValue(occurrence.eventId)
        val choices = catalog.content.choices.filter { it.id in choiceIds && it.moneyDelta <= 0 }
        if (choices.none { paid -> choices.any { other ->
                session.engine.choiceMoneyDelta(paid.id, demoMode) < session.engine.choiceMoneyDelta(other.id, demoMode) &&
                    policy.energyFor(paid.id) < policy.energyFor(other.id)
            } }) return null
        val outcomes = choices.mapNotNull { choice ->
            (session.engine.previewEventChoice(saved, occurrence.id, choice.id, demoMode) as? EngineResult.Applied)?.state
        }
        val offer = session.engine.availableDeeds(saved).firstOrNull { candidate ->
            candidate.expiresDay == day.day && outcomes.any { after ->
                session.engine.blockReason(after, EngineCommand.StartDeed(candidate.id), demoMode) == null
            }
        } ?: return null
        val event = catalog.content.events.first { it.id == offer.eventId }
        return ResourcePriorityUi(offer.id, renderPetText(catalog.displayTitle(event), saved.pet.name).asGameUiText(),
            effortCost(catalog.policies.getValue(offer.eventId).energyCost, saved.pet.name, demoMode),
            catalog.content.choices.filter { it.eventId == offer.eventId }.maxOf { it.moneyDelta },
            choices.map { choice ->
                val delta = session.engine.choiceMoneyDelta(choice.id, demoMode)
                val money = if (delta == 0L) "без траты монет" else "${Math.negateExact(delta)} монет"
                val action = renderPetText(catalog.displayAction(choice), saved.pet.name).substringBefore('\u00b7').trimEnd()
                "$action: $money, ${effortCost(policy.energyFor(choice.id), saved.pet.name, demoMode)}"
            }, selected = false)
    }

    private fun effortCost(cost: Int, petName: String, demoMode: Boolean) = if (demoMode) "Без усталости · режим бога" else when (cost) {
        0 -> "Не тратит силы"
        1 -> "Немного устанет"
        2 -> "Устанет"
        else -> "Сильно устанет"
    }.asPetEffortText(petName)

    private fun financialPosition(saved: GameState) = FinancialPosition(saved.economy.availableBalance,
        saved.economy.savingsBalance, session.catalog.mealPolicy.foodRequirement(saved))

    private fun presentationId(saved: GameState): String =
        "day:${saved.engine?.currentEvent?.id}:${saved.engine?.revision}"

    private fun primaryCommand(saved: GameState, demoMode: Boolean = session.demoModeEnabled): EngineCommand? {
        val occurrence = saved.engine?.currentEvent
        if (occurrence?.status == EventStatus.RESULT && !isProposal(saved)) return EngineCommand.AcknowledgeResult(occurrence.id)
        if (occurrence != null) {
            val rest = EngineCommand.FinishDayFromEvent(occurrence.id)
            val blocked = session.engine.blockReason(saved, rest, demoMode)
            if (blocked == null || blocked == BlockReason.MustEat) return rest
        }
        return session.advanceCommand(saved, demoMode)
    }

    private fun render() {
        // Observation can receive the saved pause/result acknowledgement before navigation exits.
        // Keep the outgoing card visible until removal; failed commands unlock and render again.
        if (!screenActive || busy) return
        if (pendingAction != null) {
            mutableState.value = mutableState.value.copy(busy = false, retryRequired = true, message = message)
            return
        }
        val saved = game ?: return
        val demoMode = session.demoModeEnabled
        val current = presentation?.takeIf { it.game === saved && it.demoMode == demoMode }
        val needsMeals = mealsShown
        if (current != null && (!needsMeals || current.includesMeals)) { publish(current); return }
        if (!mutableState.value.loading) mutableState.value = mutableState.value.copy(busy = true)
        if (projection?.isActive == true && projectionGame === saved && projectionMode == demoMode &&
            (!needsMeals || projectionIncludesMeals)) return
        projection?.cancel()
        projectionGame = saved
        projectionMode = demoMode
        projectionIncludesMeals = needsMeals
        projection = viewModelScope.launch {
            try {
                val computed = withContext(computationDispatcher) {
                    val base = current ?: buildPresentation(saved, demoMode)
                    if (needsMeals && !base.includesMeals) base.copy(includesMeals = true,
                        state = base.state.copy(meals = mealChoices(saved, session.catalog, session.engine, demoMode))) else base
                }
                if (screenActive && game === saved && session.demoModeEnabled == demoMode && !busy && pendingAction == null) {
                    presentation = computed
                    publish(computed)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (screenActive && game === saved && session.demoModeEnabled == demoMode) {
                    loading?.cancel(); loading = null
                    mutableState.value = mutableState.value.copy(loading = false, busy = false, failed = true)
                }
            }
        }
    }

    private suspend fun prepareCampaignRestart() {
        val request = pendingRestart ?: checkNotNull(session.snapshotHead()).let { head ->
            check(session.canRestartCampaign(head.state))
            CampaignRestartRequest(UUID.randomUUID().toString(), head.runId,
                head.state.engine?.revision, head.historySequence).also { pendingRestart = it }
        }
        // The old screen must not treat the deliberately absent active game as a read error.
        loading?.cancel(); loading = null
        session.prepareCampaignRestart(request)
        pendingRestart = null
        restarts.send(Unit)
    }

    private fun restartCompletedCampaign() {
        busy = true
        mutableState.value = mutableState.value.copy(busy = true, message = null)
        viewModelScope.launch {
            var completed = false
            try { prepareCampaignRestart(); completed = true }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: CampaignRestartConflictException) {
                pendingRestart = null
                message = "Приключение изменилось. Попробуй вернуться к началу ещё раз."
                load()
            } catch (_: Exception) {
                message = "Не удалось подтвердить возвращение. Повтори попытку - история сохранится один раз."
            } finally {
                if (!completed) { busy = false; render() }
            }
        }
    }

    private fun publish(current: Presentation) {
        if (current.state.resourcePriority?.offerId != selectedResourcePriority) selectedResourcePriority = null
        mutableState.value = current.state.copy(
            busy = false,
            message = message?.takeUnless { it == current.state.actionNotice } ?: current.state.message,
            reflectionAvailable = current.state.summary?.day?.let { it == reflectionDay } == true,
            resourcePriority = current.state.resourcePriority?.let { it.copy(selected = it.offerId == selectedResourcePriority) },
            showMeals = mealsShown && current.includesMeals,
            meals = if (mealsShown) current.state.meals else emptyList(),
        )
    }

    private fun buildPresentation(saved: GameState, demoMode: Boolean): Presentation {
        val day = saved.engine
        val occurrence = day?.currentEvent
        val catalog = session.catalog
        val event = occurrence?.let { catalog.content.events.single { e -> e.id == it.eventId } }
        val card = event?.let { catalog.cards[it.id] }
        val story = catalog.storyProgress(saved)
        val variant = (card?.presentation?.bodyVariants.orEmpty() + card?.variants.orEmpty())
            .firstOrNull { story.meets(it.condition) }
        val result = occurrence?.status == EventStatus.RESULT && !isProposal(saved)
        val choices = if (event != null && !result && !story.campaignComplete) catalog.content.choices.filter {
            it.eventId == event.id && it.id !in catalog.policies.getValue(event.id).disabledChoiceIds
        }.sortedBy { it.position } else emptyList()
        val blocked = choices.associate { it.id to session.engine.blockReason(saved, choiceCommand(saved, it.id), demoMode) }
        val choiceDeltas = choices.associate { it.id to session.engine.choiceMoneyDelta(it.id, demoMode) }
        val primary = if (story.campaignComplete) null else primaryCommand(saved, demoMode)
        val primaryBlock = primary?.let { session.engine.blockReason(saved, it, demoMode) }
        val restFromCard = primary is EngineCommand.FinishDayFromEvent
        val summary = if (story.campaignComplete) null else session.engine.daySummary(saved)
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
            story.campaignComplete -> renderPetText(presentation.body ?: "Мы открыли часть тайн Смотрителей. Их сила поможет вернуться к началу и пройти другими путями. История этого приключения сохранится.", saved.pet.name)
            summary != null -> "${saved.pet.name} отдыхает."
            result -> if (event?.type == EventType.EARNING) "Дело выполнено. Получено ${completedChoice?.moneyDelta ?: 0} монет."
                else "Этот шаг истории завершён."
            event != null -> renderPetText(presentation.body ?: variant?.body ?: event.description, saved.pet.name).asGameUiText()
            day == null -> ""
            day.phase == DayPhase.READY_TO_END -> "На сегодня всё. До сна ещё можно выбрать короткое задание в разделе «Дела»."
            else -> ""
        }
        val mealPrice = catalog.mealPolicy.basicMeal.price
        val feedingChoices = event?.let { catalog.policies.getValue(it.id).feedsPetChoiceIds }.orEmpty()
        val foodWarning = if (!demoMode && day?.ateToday == false && choices.any {
            it.id !in feedingChoices && it.moneyDelta < 0 && blocked[it.id] == null &&
                saved.economy.availableBalance + it.moneyDelta < mealPrice
        }) "После этой траты на обычный обед не хватит. ${saved.pet.name} ещё не поел сегодня." else null
        // Derive the explanation from the same guards that replace the button. Keep it separate
        // from transient feedback so a save error or feeding message cannot hide the current need.
        val actionNotice = when {
            restFromCard || (primary == EngineCommand.FinishDay && (!demoMode && day?.energy == 0 ||
                session.engine.blockReason(saved, EngineCommand.OpenNextEvent, demoMode) == BlockReason.MustSleep)) ->
                if (primaryBlock == BlockReason.MustEat) "${saved.pet.name} устал. Сначала поест, потом отдохнёт. Остальное отложим на завтра."
                else BlockReason.MustSleep.playerMessage(saved.pet.name)
            primaryBlock == BlockReason.MustEat || blocked.values.any { it == BlockReason.MustEat } ->
                BlockReason.MustEat.playerMessage(saved.pet.name)
            else -> null
        }
        val choiceBlock = blocked.values.takeIf { reasons -> reasons.none { it == null } }
            ?.firstOrNull { it != null && it != BlockReason.MustEat }
        val effectiveMessage = if (actionNotice == null) choiceBlock?.playerMessage(saved.pet.name)
                ?: foodWarning ?: primaryBlock?.playerMessage(saved.pet.name) else null
        val goalSelected = catalog.goals.selectedGoal(saved) != null
        val offeredDeed = if (isDeed(saved)) day?.deeds?.find { offer ->
            offer.id == occurrence?.deedOfferId || offer.id == "${occurrence?.id}:offer"
        } else null
        val resourcePriority = if (restFromCard) null else resourcePriority(saved, choices.filter { blocked[it.id] == null }.map { it.id }, demoMode)
        val state = DayUiState(
            loading = false, petName = saved.pet.name,
            audioOccurrenceId = occurrence?.id.takeIf { !story.campaignComplete && !result && summary == null },
            eventMedia = presentation.media,
            summary = summary?.toUiState(catalog, saved.pet.name, saved.economy),
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
                story.campaignComplete -> "Другие пути ещё ждут"
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
            impact = if (story.campaignComplete || result || storyIntroduction || purchaseCard) "" else if (isDeed(saved)) "Награда: до ${choices.single().moneyDelta} монет" else renderPetText(card?.impact.orEmpty(), saved.pet.name).asGameUiText(),
            effort = if (story.campaignComplete || result || simpleStoryAction || !presentation.showEffort) "" else if (demoMode) "Без усталости · режим бога" else
                card?.effort.orEmpty().asGameUiText().takeUnless {
                    it in setOf("Без траты сил", "Без трат сил", "Без расхода сил", "Не тратит силы", "Можно отказаться")
                }.orEmpty().asPetEffortText(saved.pet.name),
            scene = variant?.scene ?: card?.scene, character = variant?.character ?: card?.character,
            options = (if (restFromCard) emptyList() else choices).map { DayOption(it.id, if (isDeed(saved)) {
                if (goalSelected) "Заработать на цель" else "Выполнить дело"
            } else if (it.moneyDelta < 0 && choiceDeltas.getValue(it.id) == 0L) "Получить бесплатно"
                else catalog.displayAction(it).asGameActionLabel().asPetEffortText(saved.pet.name),
                blocked[it.id] == BlockReason.MustEat, blocked[it.id] == null || blocked[it.id] == BlockReason.MustEat,
                if (it.moneyDelta < 0 && choiceDeltas.getValue(it.id) == 0L) "Бесплатно · режим бога"
                else if (it.moneyDelta < 0 && blocked[it.id] !is BlockReason.InsufficientMoney)
                    EconomyOperations.quote(saved.economy, Math.negateExact(it.moneyDelta),
                        SpendingKind.forEvent(checkNotNull(event).type)).playerDescription(SpendingKind.forEvent(event.type))
                else (blocked[it.id] as? BlockReason.InsufficientMoney)?.takeIf { choiceBlock !is BlockReason.InsufficientMoney }
                    ?.let { reason -> "Не хватает ${reason.missing} монет" }) },
            later = if (event != null && !result && !story.campaignComplete) {
                if (isDeed(saved)) "Сделать позже"
                else if (card != null) card.later?.let { renderPetText(it, saved.pet.name).asGameActionLabel() }
                else "Вернуться позже"
            } else null,
            primary = if (story.campaignComplete) "Воспользоваться силой" else primary?.let { when {
                primaryBlock == BlockReason.MustEat -> "Покормить"
                result -> "Вернуться"
                summary != null -> "Начать день ${summary.day.toLong() + 1}"
                day == null -> "Начать день 1"
                it == EngineCommand.FinishDay || restFromCard -> "Закончить день"
                else -> "Продолжить день"
            } }, primaryNeedsFood = primaryBlock == BlockReason.MustEat,
            primarySpending = if (occurrence == null && primary != null && primaryBlock != BlockReason.MustEat)
                session.engine.advanceSpending(saved, primary, demoMode)?.let {
                    if (demoMode && it.quote.affordable && it.quote.parts.isEmpty()) "Бесплатно · режим бога"
                    else it.quote.playerDescription(it.kind)
                } else null,
            message = effectiveMessage, actionNotice = actionNotice,
        )
        return Presentation(saved, demoMode, state, primary, campaignComplete = story.campaignComplete)
    }
}
