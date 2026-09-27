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
import ru.nksk.lctapp.core.ui.game.eventCompletionMessage
import ru.nksk.lctapp.core.ui.game.restingPetArtwork
import ru.nksk.lctapp.core.ui.game.asGameActionLabel
import ru.nksk.lctapp.core.ui.game.asGameUiText
import ru.nksk.lctapp.core.ui.game.cosmeticArtwork
import ru.nksk.lctapp.core.ui.game.AdventurePetPresentation
import ru.nksk.lctapp.core.ui.game.EventSceneArtwork
import ru.nksk.lctapp.core.ui.game.eventSceneArtwork
import ru.nksk.lctapp.core.ui.game.eventSceneBackground
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.R
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.content.ItemOperation
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.analytics.DecisionContext
import ru.nksk.lctapp.domain.analytics.FinancialPosition
import ru.nksk.lctapp.domain.pet.PetCosmetics

internal data class DayOption(val id: String, val label: String, val needsFood: Boolean, val enabled: Boolean, val spending: String? = null)
internal data class StoryGameRequest(val occurrenceId: String, val choiceId: String)
internal data class MealOption(val id: String, val label: String, val enabled: Boolean, val spending: String? = null, val consequence: String? = null)
internal data class ResourcePriorityUi(val offerId: String, val title: String, val effort: String,
    val reward: Long, val comparisons: List<String>, val selected: Boolean)
internal data class DayUiState(
    val loading: Boolean = true, val failed: Boolean = false, val busy: Boolean = false,
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
    val storyIntroduction: Boolean = false,
    val bakeryBunCard: Boolean = false,
    val purchaseArtworkRes: Int? = null,
    val pet: AdventurePetPresentation? = null,
    val eventArtwork: EventSceneArtwork? = null,
    val eventBackgroundRes: Int = R.drawable.menu_village,
    val deedDeadline: String? = null,
)

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
                val command = choiceCommand(saved, action.id,
                    selectedResourcePriority.takeIf { it == mutableState.value.resourcePriority?.offerId })
                execute(saved, command, leave = !miniGame && command !is EngineCommand.StartStoryGame,
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
            is DayAction.Feed -> execute(saved, EngineCommand.Feed(action.id), fed = true)
            DayAction.Later -> saved.engine?.currentEvent?.let {
                execute(saved, if (isProposal(saved)) EngineCommand.DismissDeedProposal(it.id)
                    else if (it.status == EventStatus.RESULT) EngineCommand.AcknowledgeResult(it.id)
                    else EngineCommand.PauseEvent(it.id), leave = true)
            }
            DayAction.Retry -> Unit
            is DayAction.FinancialContextPresented -> Unit
        }
    }

    private fun execute(saved: GameState, command: EngineCommand, leave: Boolean = false,
        fed: Boolean = false, openGame: Boolean = false) {
        val context = mutableState.value.financialContext?.let { displayed ->
            val presented = displayed.presentationId == shownFinancialContext
            displayed.copy(informationPresented = presented,
                complete = presented && displayed.before == financialPosition(saved) &&
                    displayed.presentationId == presentationId(saved))
        }
        busy = true
        message = null
        mutableState.value = mutableState.value.copy(busy = true)
        viewModelScope.launch {
            var exitRequested = false
            try {
                when (val result = session.dispatch(EngineRequest(UUID.randomUUID().toString(), saved.engine?.revision, command, context))) {
                    is EngineResult.Applied -> {
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
                            exitRequested = true
                            games.send(checkNotNull(result.state.engine?.currentEvent).id)
                        }
                        if (command is EngineCommand.StartStoryGame) {
                            exitRequested = true
                            storyGames.send(StoryGameRequest(command.occurrenceId, command.choiceId))
                        }
                        if (leave && result.state.economy.planning == null) {
                            exitRequested = true
                            exits.send(if (command is EngineCommand.CompleteEvent)
                                eventCompletionMessage(saved, result.state, command, session.catalog)
                                else if (command is EngineCommand.PauseEvent && result.state.engine?.events?.any {
                                    it.id == command.occurrenceId && it.status == EventStatus.CARRIED_ACTIVE
                                } == true) "Вернёмся к этой ситуации завтра. Пока можно заняться делами." else null)
                        }
                    }
                    is EngineResult.Blocked -> {
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
        return ResourcePriorityUi(offer.id, renderPetText(event.title, saved.pet.name).asGameUiText(),
            effortCost(catalog.policies.getValue(offer.eventId).energyCost),
            catalog.content.choices.filter { it.eventId == offer.eventId }.maxOf { it.moneyDelta },
            choices.map { choice ->
                val money = if (choice.moneyDelta == 0L) "без траты монет" else "${Math.negateExact(choice.moneyDelta)} монет"
                val action = renderPetText(choice.text, saved.pet.name).substringBefore('\u00b7').trimEnd()
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
        saved.economy.savingsBalance, foodCostUntilWeekEnd(saved, session.catalog.meals.filter { it.price > 0 }.minOf { it.price }))

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
        val purchaseCard = event?.type == EventType.WANT && !result && !restFromCard && summary == null
        val abilityCopy = event?.takeIf { !result }?.let { storyAbilityCopy(it.id, saved.pet.name) }
        val purchaseCopy = if (purchaseCard) purchaseCardCopy(checkNotNull(event).id, saved.pet.name) else null
        // Presentation override for the existing immutable bakery offer. The same
        // saved event and choices still own its price, feeding and mood effects.
        val bakeryBunCard = event?.id == "figma-2654-2-purchase-v2" && !result && !restFromCard && summary == null
        val purchaseArtworkRes = if (event?.type == EventType.WANT && !result && !restFromCard) {
            when (event.id) {
                "figma-2654-2-purchase-v2" -> R.drawable.prop_bakery_bun
                "figma-2654-50-purchase-v2" -> R.drawable.prop_fair_explorer_hat
                "figma-2654-98-purchase-v2" -> R.drawable.prop_fair_ring_toss
                "figma-2654-146-purchase-v2" -> R.drawable.prop_fair_compass_keychain
                "figma-2654-194-purchase-v2" -> R.drawable.prop_fair_toy_boat
                else -> {
                    val paidChoices = choices.filter { it.moneyDelta < 0 }.map { it.id }.toSet()
                    catalog.content.choiceItemEffects.asSequence()
                        .filter { it.choiceId in paidChoices && it.operation == ItemOperation.ADD }
                        .mapNotNull { PetCosmetics.forItem(it.itemId)?.lookId?.let(::cosmeticArtwork) }
                        .firstOrNull()
                }
            }
        } else null
        // Lore does not become a money decision just because it has several
        // replies or needs effort. Only a monetary consequence needs this summary.
            val simpleStoryAction = event?.type == EventType.STORY && !result && choices.size == 1 &&
            event.moneyDeltaOnStart == 0L && choices.single().moneyDelta == 0L &&
            catalog.policies.getValue(event.id).energyFor(choices.single().id) == 0
        val storyIntroduction = simpleStoryAction && event?.id == catalog.introductionId && summary == null
        val completedChoice = occurrence?.let { active -> saved.story.decisions.find { it.id == "${active.id}:decision" } }
            ?.let { decision -> catalog.content.choices.find { it.id == decision.choiceId } }
        val body = when {
            summary != null -> "${saved.pet.name} отдыхает."
            result -> if (event?.type == EventType.EARNING) "Дело выполнено. Получено ${completedChoice?.moneyDelta ?: 0} монет."
                else "Этот шаг истории завершён."
            storyIntroduction -> "Смотритель зовёт нас на Ночь наблюдений.\n\nПоможем подготовить телескоп и разгадаем старые загадки."
            purchaseCopy != null -> purchaseCopy.body
            abilityCopy != null -> abilityCopy.body
            event != null -> renderPetText(variant?.body ?: event.description, saved.pet.name).asGameUiText()
            day == null -> ""
            day.phase == DayPhase.READY_TO_END -> "Все события на сегодня закончились. До сна ещё можно выполнить короткое дело из списка «Дела»."
            else -> ""
        }
        val mealPrice = catalog.meals.first { it.price > 0 }.price
        val foodWarning = if (!bakeryBunCard && day?.ateToday == false && choices.any {
            it.moneyDelta < 0 && blocked[it.id] == null && saved.economy.availableBalance + it.moneyDelta < mealPrice
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
            summary = summary?.toUiState(catalog, saved.pet.name, saved.economy),
            reflectionAvailable = summary != null && summary.day == reflectionDay,
            restingPetRes = summary?.let { restingPetArtwork(saved.pet) },
            resourcePriority = resourcePriority,
            storyIntroduction = storyIntroduction,
            bakeryBunCard = bakeryBunCard,
            purchaseArtworkRes = purchaseArtworkRes,
            deedDeadline = offeredDeed?.let { offer -> when (offer.expiresDay - checkNotNull(day).day) {
                0 -> "Успеть до конца сегодня"
                1 -> "Можно выполнить сегодня или завтра"
                else -> "Можно выполнить до конца дня ${offer.expiresDay}"
            } },
            pet = PetEventCondition.forPresentation(saved, session.catalog.policies).toAdventurePetPresentation(),
            eventArtwork = eventSceneArtwork(event?.id, variant?.character ?: card?.character),
            eventBackgroundRes = eventSceneBackground(event?.id, variant?.scene ?: card?.scene),
            practiceRequired = blocked.values.any { it is BlockReason.FinancialPracticeRequired } || primaryBlock is BlockReason.FinancialPracticeRequired,
            title = when {
                summary != null -> "День ${summary.day} завершён"
                result -> "Готово!"
                purchaseCopy != null -> purchaseCopy.title
                abilityCopy != null -> abilityCopy.title
                event != null -> renderPetText(event.title, saved.pet.name).asGameUiText()
                primaryBlock == BlockReason.MustEat -> "Пора подкрепиться"
                primary == EngineCommand.FinishDay -> "Пора отдохнуть"
                day == null -> "Начнём приключение"
                else -> "Продолжим приключение"
            },
            body = if (event == null && summary == null && actionNotice != null) "" else body,
            category = if (summary != null) "Итоги дня" else card?.category?.let { renderPetText(it, saved.pet.name).asGameUiText() } ?: saved.pet.name,
            impact = if (result || storyIntroduction || purchaseCard) "" else if (isDeed(saved)) "Награда: до ${choices.single().moneyDelta} монет" else renderPetText(card?.impact.orEmpty(), saved.pet.name).asGameUiText(),
            effort = if (result || simpleStoryAction || bakeryBunCard) "" else
                renderPetText(card?.effort.orEmpty(), saved.pet.name).asGameUiText().takeUnless {
                    it in setOf("Без траты сил", "Без трат сил", "Без расхода сил", "Не тратит силы", "Можно отказаться")
                }.orEmpty(),
            scene = variant?.scene ?: card?.scene, character = variant?.character ?: card?.character,
            options = (if (restFromCard) emptyList() else choices).map { DayOption(it.id, if (isDeed(saved)) {
                if (goalSelected) "Заработать на цель" else "Выполнить дело"
            } else if (storyIntroduction) "В обсерваторию" else if (purchaseCard) {
                if (it.moneyDelta < 0) purchaseCopy?.action ?: "Купить" else "Пройти мимо"
            } else abilityCopy?.action?.takeIf { _ -> choices.size == 1 }
                ?: renderPetText(it.text, saved.pet.name).asGameActionLabel(),
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
            meals = catalog.meals.filter { it.price > 0 || saved.economy.availableBalance < catalog.meals.first().price }.map {
                val quote = EconomyOperations.quote(saved.economy, it.price, SpendingKind.FEEDING)
                MealOption(it.id, if (it.price == 0L) "Поесть бесплатно" else "Обычный обед за ${it.price} монет",
                    session.engine.blockReason(saved, EngineCommand.Feed(it.id)) == null,
                    quote.playerDescription(SpendingKind.FEEDING),
                    "После еды сегодня понадобится отдых. Утром будем немного уставшими.".takeIf { _ -> it.price == 0L })
            }, message = effectiveMessage, actionNotice = actionNotice,
        )
    }
}
