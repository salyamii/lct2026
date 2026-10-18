package ru.nksk.lctapp.feature.tasks.ui

import ru.nksk.lctapp.domain.pet.renderPetText

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.nksk.lctapp.core.ui.game.GameActionAttempt
import ru.nksk.lctapp.core.ui.game.playerMessage
import ru.nksk.lctapp.core.ui.game.deedCompletionMessage
import ru.nksk.lctapp.core.ui.game.eventCompletionMessage
import ru.nksk.lctapp.core.ui.game.eventSceneArtwork
import ru.nksk.lctapp.core.ui.game.eventSceneBackground
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.minigame.*
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.feature.tasks.navigation.ChapterRepairPayment

enum class DeedGameType { MEMORY, COMPARISON, PRECISION, LIGHTS, SEQUENCE, PIPES, DIFFERENCES, STACKING,
    TELESCOPE, MARK, WHEEL, STRAP, SIGNAL, ROUTE_MAP, BOAT,
    NEED_WANT, REAL_DISCOUNT, COIN_BALANCE, FINANCIAL_TRAP, EXTRA_PURCHASE }
data class DeedGamePresentation(val title: String, val maximumReward: Long, val canPlay: Boolean,
    val storyAction: Boolean = false, val sceneRes: Int? = null, val instructions: String? = null,
    val activityArtworkRes: Int? = null)
internal data class DeedGameUiState(
    val loading: Boolean = true,
    val type: DeedGameType? = null,
    val presentation: DeedGamePresentation? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val canRetry: Boolean = false,
    val audioOccurrenceId: String? = null,
    val eventMedia: EventMedia = EventMedia(),
    val canSkipGame: Boolean = false,
    val demoMode: Boolean = false,
    val reward: DeedRewardUiState? = null,
    val repairCoins: Int = 0,
)

/** Connects an actual offered deed to a transient board and one atomic engine outcome. */
@HiltViewModel
internal class DeedGameViewModel @Inject constructor(
    private val session: GameSession,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeedGameUiState())
    val uiState = mutableState.asStateFlow()
    private val exits = Channel<String?>(Channel.BUFFERED)
    val exit = exits.receiveAsFlow()
    private var occurrenceId: String? = null
    private var choiceId: String? = null
    private var latest: GameState? = null
    private var observer: Job? = null
    private var busy = false
    private var message: String? = null
    private class PendingAction(val before: GameState, val command: EngineCommand) {
        var attempt: GameActionAttempt? = null
    }
    private var pending: PendingAction? = null
    private var rejectedAction: EngineCommand? = null
    private val comparisonEvidence = linkedMapOf<String, PriceQuizEvidence>()
    private val comparisonWrites = Mutex()
    private var comparisonWriter: Job? = null
    private var comparisonSaveFailed = false
    private var leaveWhenLoaded = false
    private var rewardOutcome: DeedRewardOutcome? = null
    private var rewardReceiptNeeded = false
    private var rewardEditing = false
    private var rewardInput = ""
    private var rewardDeposited = 0L
    private var repairWriting = false

    fun load(id: String, storyChoiceId: String? = null, reload: Boolean = false) {
        if (!reload && occurrenceId == id && choiceId == storyChoiceId && observer?.isActive == true) return
        if (occurrenceId != id || choiceId != storyChoiceId) comparisonEvidence.clear()
        occurrenceId = id
        choiceId = storyChoiceId
        observer?.cancel()
        observer = viewModelScope.launch {
            try {
                session.prepare()
                if (savedState.contains(REWARD_REQUEST)) {
                    latest = checkNotNull(session.read())
                    restoreReward()
                }
                session.observe().collect { latest = checkNotNull(it); render() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = DeedGameUiState(loading = false, message = "Не удалось загрузить дело. Попробуй ещё раз.", canRetry = true)
            }
        }
    }

    fun finishMemory(state: MemoryState) { DeedGameScore.fromMemory(state)?.let(::finish) }
    fun finishComparison(state: PriceQuizState, evidence: PriceQuizEvidence? = null) {
        evidence?.let(::recordComparisonAnswers)
        DeedGameScore.fromComparison(state)?.let(::finish)
    }
    fun finishPrecision(state: TargetStopState) { DeedGameScore.fromPrecision(state)?.let(::finish) }
    fun finishLights(state: LightsState) { DeedGameScore.fromLights(state)?.let(::finish) }
    fun finishSequence(state: SequenceState) { DeedGameScore.fromSequence(state)?.let(::finish) }
    fun finishPipes(state: PipesState) { DeedGameScore.fromPipes(state)?.let(::finish) }
    fun finishDifferences(state: DifferencesState) { DeedGameScore.fromDifferences(state)?.let(::finish) }
    fun finishStacking(state: StackingState) { DeedGameScore.fromStacking(state)?.let(::finish) }

    fun finishFinancialGame(score: DeedGameScore) {
        if (choiceId != null || mutableState.value.presentation?.canPlay != true ||
            score.kind !in financialGameKinds || mutableState.value.type?.name != score.kind.name) return
        finish(score)
    }

    fun finishChapterGame(score: DeedGameScore) {
        if (mutableState.value.presentation?.canPlay != true || score.kind !in chapterGameKinds || repairUnconfirmed()) return
        finish(score)
    }

    /** The child board owns only the collision. This host owns the durable payment intent. */
    suspend fun payBoatRepair(requestId: String, rockIndex: Int): ChapterRepairPayment {
        if (busy || repairWriting || pending != null || requestId.isBlank()) return ChapterRepairPayment.FAILED
        val id = occurrenceId ?: return ChapterRepairPayment.FAILED
        val command = EngineCommand.PayBoatRepair(id, rockIndex)
        repairWriting = true
        render()
        return try {
            val stored = savedState.get<String>(REPAIR_REQUEST)
            val same = savedState.get<String>(REPAIR_KEY) == requestId
            if (!same && repairUnconfirmed()) return ChapterRepairPayment.FAILED
            val attempt = if (same && stored != null) {
                val request = Json.decodeFromString<EngineRequest>(stored)
                check(request.command == command)
                GameActionAttempt.restore(request)
            } else {
                val current = checkNotNull(session.read())
                GameActionAttempt.prepare(current, command, demoMode = session.demoModeEnabled).also {
                    savedState[REPAIR_KEY] = requestId
                    savedState[REPAIR_REQUEST] = Json.encodeToString(it.request)
                    savedState[REPAIR_CONFIRMED] = false
                }
            }
            when (val result = attempt.submit(session)) {
                is EngineResult.Applied -> {
                    latest = result.state
                    savedState[REPAIR_CONFIRMED] = true
                    ChapterRepairPayment.PAID
                }
                is EngineResult.Blocked -> {
                    // A definite rejection is safe to replace on the child's explicit next attempt.
                    savedState.remove<String>(REPAIR_REQUEST)
                    savedState.remove<String>(REPAIR_KEY)
                    savedState[REPAIR_CONFIRMED] = true
                    latest = session.read() ?: latest
                    if (result.reason is BlockReason.InsufficientMoney) ChapterRepairPayment.NOT_ENOUGH_COINS
                    else ChapterRepairPayment.FAILED
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) {
            ChapterRepairPayment.FAILED
        } finally {
            repairWriting = false
            render()
        }
    }

    private fun repairUnconfirmed() = savedState.get<String>(REPAIR_REQUEST) != null &&
        savedState.get<Boolean>(REPAIR_CONFIRMED) != true

    private fun finish(score: DeedGameScore) {
        if (busy || pending != null || rejectedAction != null || rewardOutcome != null || rewardReceiptNeeded) return
        val id = occurrenceId ?: return
        execute(choiceId?.let { EngineCommand.CompleteStoryGame(id, it, score) }
            ?: EngineCommand.CompleteDeed(id, score))
    }

    fun skipGame() {
        if (busy || repairWriting || repairUnconfirmed() || pending != null || rejectedAction != null || !session.demoModeEnabled ||
            !uiState.value.canSkipGame) return
        val id = occurrenceId ?: return
        execute(EngineCommand.SkipMiniGame(id, choiceId))
    }

    fun leave() {
        if (busy || repairWriting || repairUnconfirmed()) return
        if (rewardOutcome != null) {
            if (pending != null || rewardReceiptNeeded) return
            if (rewardEditing) { cancelRewardTransfer(); return }
            savedState[REWARD_DISMISSED] = true
            exit()
            return
        }
        if (pending != null) { exit(); return }
        if (latest == null) {
            leaveWhenLoaded = true
            occurrenceId?.let { load(it, choiceId) }
            return
        }
        val active = latest?.engine?.currentEvent
        if (active?.id == occurrenceId && active?.status == EventStatus.ACTIVE) {
            execute(EngineCommand.PauseEvent(checkNotNull(active).id))
        } else exit()
    }

    fun retry() {
        if (busy) return
        pending?.let { submit(it); return }
        if (rewardReceiptNeeded) {
            occurrenceId?.let { load(it, choiceId, reload = true) }
            return
        }
        // A definite stale rejection can be retried only as an explicit new intent.
        rejectedAction?.let { execute(it); return }
        if (comparisonSaveFailed) persistComparisonAnswers() else occurrenceId?.let { load(it, choiceId) }
    }

    fun openRewardTransfer() {
        val shown = uiState.value.reward ?: return
        if (busy || pending != null || rewardReceiptNeeded || shown.maximum <= 0 || rewardDeposited > 0) return
        rewardEditing = true
        rewardInput = ""
        savedState[REWARD_EDITING] = true
        savedState[REWARD_INPUT] = rewardInput
        message = null
        render()
    }

    fun changeRewardTransferInput(input: String) {
        if (busy || pending != null || rewardReceiptNeeded || !rewardEditing || !input.all(Char::isDigit) || input.length > 19) return
        rewardInput = input
        savedState[REWARD_INPUT] = input
        message = null
        render()
    }

    fun cancelRewardTransfer() {
        if (busy || pending != null || rewardReceiptNeeded) return
        rewardEditing = false
        savedState[REWARD_EDITING] = false
        message = null
        render()
    }

    fun confirmRewardTransfer() {
        if (busy || pending != null || rewardReceiptNeeded || !rewardEditing || rewardDeposited > 0 || session.demoModeEnabled) return
        val game = latest ?: return
        val reward = rewardOutcome ?: return
        val amount = rewardInput.toLongOrNull() ?: return
        if (amount <= 0 || amount > reward.transferMaximum(game, session.catalog)) return
        execute(EngineCommand.DepositSavings(amount, expected = SavingsTransferExpectation(
            game.economy.availableBalance, game.economy.savingsBalance,
            game.selectedSavingItemId, game.economy.plan)))
    }

    private suspend fun restoreReward() {
        val completion = savedState.get<String>(REWARD_REQUEST)?.let { Json.decodeFromString<EngineRequest>(it) }
            ?: return
        val command = completion.command as? EngineCommand.CompleteDeed ?: return
        if (command.occurrenceId != occurrenceId || choiceId != null || savedState.get<Boolean>(REWARD_DISMISSED) == true) return
        if (savedState.get<Boolean>(REWARD_REJECTED) == true) {
            rejectedAction = command
            message = "Игра изменилась. Нажми «Повторить», чтобы заново проверить и сохранить действие."
            return
        }
        rewardReceiptNeeded = true
        val receipt = session.commandReceipt(completion.id)
        if (receipt == null) {
            // The process may have stopped before the write. Retry the original intent explicitly.
            pending = PendingAction(checkNotNull(latest), command).also { it.attempt = GameActionAttempt.restore(completion) }
            message = "Не удалось проверить результат. Нажми «Повторить»."
            rewardReceiptNeeded = false
            return
        }
        rewardOutcome = deedRewardOutcome(receipt, command.occurrenceId, session.catalog)
        message = null
        rewardEditing = savedState[REWARD_EDITING] ?: false
        rewardInput = savedState[REWARD_INPUT] ?: ""
        val transfer = savedState.get<String>(REWARD_TRANSFER)?.let { Json.decodeFromString<EngineRequest>(it) }
        if (transfer == null || rewardOutcome == null) { rewardReceiptNeeded = false; return }
        val deposit = checkNotNull(transfer.command as? EngineCommand.DepositSavings)
        val transferReceipt = session.commandReceipt(transfer.id)
        if (transferReceipt != null) {
            check(transferReceipt.request?.copy(demoMode = transfer.demoMode) == transfer)
            val deposited = checkNotNull(transferReceipt.after).economy.savingsBalance -
                checkNotNull(transferReceipt.before).economy.savingsBalance
            check(deposited == deposit.amount)
            rewardDeposited = deposited
            rewardEditing = false
            savedState[REWARD_EDITING] = false
            message = null
        } else {
            pending = PendingAction(checkNotNull(latest), deposit).also { it.attempt = GameActionAttempt.restore(transfer) }
            message = "Не удалось проверить перевод. Нажми «Повторить»."
        }
        rewardReceiptNeeded = false
    }

    fun recordComparisonAnswers(evidence: PriceQuizEvidence) {
        if (evidence.answers.isEmpty()) return
        comparisonEvidence[evidence.seriesId] = evidence
        if (!busy) persistComparisonAnswers()
    }

    private fun persistComparisonAnswers() {
        if (comparisonWriter?.isActive == true || comparisonEvidence.isEmpty()) return
        comparisonWriter = viewModelScope.launch {
            try {
                flushComparisonAnswers()
                if (comparisonSaveFailed) { comparisonSaveFailed = false; message = null; render() }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                comparisonSaveFailed = true
                message = "Не удалось сохранить ответы. Нажми «Повторить»."
                render()
            }
        }
    }

    private suspend fun flushComparisonAnswers() = comparisonWrites.withLock {
        val id = occurrenceId ?: return@withLock
        while (true) {
            val batches = comparisonEvidence.values.toList()
            if (batches.isEmpty()) return@withLock
            val game = checkNotNull(session.read())
            val occurrence = checkNotNull(game.engine?.events?.find { it.id == id }) { "This comparison belongs to an unavailable deed" }
            check(gameKind(occurrence) == DeedGameKind.COMPARISON)
            val eventIds = batches.flatMap { PriceQuizEvidenceMapper.eventIds(it, id) }.toSet()
            val recorded = checkNotNull(session.recordedFacts(eventIds)) { "History is not available" }
            val existing = recorded.facts.associateBy { it.eventId }
            val version = session.contentFingerprint
            val candidates = batches.flatMap { evidence ->
                PriceQuizEvidenceMapper.facts(evidence, id, recorded.runId,
                    Math.addExact(recorded.sequence, 1), game.engine?.day,
                    game.financial.currentPeriod?.id, version, session.catalog.rules.id)
            }
            for (fact in candidates) existing[fact.eventId]?.let { stored ->
                check(stored.episodeId == fact.episodeId && stored.detail == fact.detail) { "Conflicting comparison answer" }
            }
            val missing = candidates.filter { it.eventId !in existing }
            if (missing.isEmpty()) {
                // The indexed read may have suspended while another answer arrived.
                if (batches == comparisonEvidence.values.toList()) return@withLock
                continue
            }
            if (!session.recordFacts(missing)) {
                // Demo answers intentionally produce no evidence, so a read-back retry cannot complete.
                batches.forEach { if (comparisonEvidence[it.seriesId] == it) comparisonEvidence.remove(it.seriesId) }
                return@withLock
            }
            // An answer may have arrived during the write; loop before allowing completion or exit.
        }
    }

    private fun execute(command: EngineCommand) {
        val game = latest ?: return
        if (busy) return
        rejectedAction = null
        val action = PendingAction(game, command)
        pending = action
        submit(action)
    }

    private fun submit(action: PendingAction) {
        if (busy) return
        val game = action.before
        val command = action.command
        busy = true
        message = null
        mutableState.value = mutableState.value.copy(busy = true, message = null,
            canSkipGame = false,
            presentation = mutableState.value.presentation?.copy(canPlay = false))
        viewModelScope.launch {
            var leaving = false
            var committedResult = false
            try {
                if (command !is EngineCommand.SkipMiniGame) flushComparisonAnswers()
                comparisonSaveFailed = false
                val attempt = action.attempt ?: run {
                    val evidence = if (command is EngineCommand.CompleteStoryGame)
                        eventGameStartEvidence(session.latestCommand(), game, command) else null
                    val submitted = if (command is EngineCommand.CompleteStoryGame)
                        command.copy(resourcePriorityOfferId = evidence?.priorityOfferId) else command
                    GameActionAttempt.prepare(game, submitted, evidence?.context,
                        demoMode = session.demoModeEnabled).also { action.attempt = it }
                }
                if (command is EngineCommand.CompleteDeed && !attempt.request.demoMode &&
                    game.selectedSavingItemId != null && session.catalog.goals.selectedGoal(game) != null) {
                    savedState[REWARD_REQUEST] = Json.encodeToString(attempt.request)
                    savedState[REWARD_REJECTED] = false
                }
                if (command is EngineCommand.DepositSavings) {
                    savedState[REWARD_TRANSFER] = Json.encodeToString(attempt.request)
                }
                when (val result = attempt.submit(session)) {
                    is EngineResult.Applied -> {
                        pending = null
                        latest = result.state
                        committedResult = true
                        if (command is EngineCommand.DepositSavings) {
                            // Confirm the original transfer, including after a lost reply or recreation.
                            rewardReceiptNeeded = true
                            restoreReward()
                            return@launch
                        }
                        if (command is EngineCommand.CompleteDeed && savedState.get<String>(REWARD_REQUEST) != null &&
                            !attempt.request.demoMode) {
                            rewardReceiptNeeded = true
                            val receipt = checkNotNull(session.commandReceipt(attempt.request.id))
                            rewardOutcome = deedRewardOutcome(receipt, command.occurrenceId, session.catalog)
                            rewardReceiptNeeded = false
                            if (rewardOutcome != null) return@launch
                        }
                        leaving = true
                        val committed = if (command is EngineCommand.SkipMiniGame) null
                            else attempt.committedState(session, result.state)
                        exits.send(when (command) {
                            is EngineCommand.CompleteDeed -> committed?.let {
                                deedCompletionMessage(it.economy.balance - game.economy.balance)
                            } ?: "Дело выполнено!"
                            is EngineCommand.CompleteStoryGame -> committed?.let {
                                eventCompletionMessage(game, it,
                                    EngineCommand.CompleteEvent(command.occurrenceId, command.choiceId), session.catalog)
                            } ?: "Готово!"
                            is EngineCommand.SkipMiniGame -> "Игра пропущена · режим бога"
                            else -> null
                        })
                    }
                    is EngineResult.Blocked -> {
                        pending = null
                        if (command is EngineCommand.CompleteDeed && savedState.contains(REWARD_REQUEST)) {
                            if (result.reason == BlockReason.StaleRevision) savedState[REWARD_REJECTED] = true
                            else savedState.remove<String>(REWARD_REQUEST)
                        }
                        if (command is EngineCommand.DepositSavings) {
                            savedState.remove<String>(REWARD_TRANSFER)
                            message = result.reason.playerMessage(game.pet.name)
                        } else if (result.reason == BlockReason.StaleRevision) {
                            rejectedAction = command
                            message = "Игра изменилась. Нажми «Повторить», чтобы заново проверить и сохранить действие."
                        } else message = result.reason.playerMessage(game.pet.name)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                message = if (committedResult && command is EngineCommand.DepositSavings)
                    "Не удалось проверить перевод. Нажми «Повторить»."
                    else if (committedResult) "Не удалось показать награду. Нажми «Повторить»."
                    else "Не удалось сохранить результат. Нажми «Повторить»."
            } finally {
                if (!leaving) { busy = false; render() }
            }
        }
    }

    private fun exit() {
        busy = true
        mutableState.value = mutableState.value.copy(busy = true,
            canSkipGame = false,
            presentation = mutableState.value.presentation?.copy(canPlay = false))
        exits.trySend(null)
    }

    private fun render() {
        if (busy) return
        rewardOutcome?.let { reward ->
            val game = latest ?: return
            mutableState.value = mutableState.value.copy(
                loading = false, busy = false, message = message,
                canRetry = pending != null || rewardReceiptNeeded, canSkipGame = false,
                presentation = mutableState.value.presentation?.copy(canPlay = false),
                reward = DeedRewardUiState(reward.earned, reward.itemTitle,
                    if (rewardDeposited > 0 || session.demoModeEnabled) 0 else reward.transferMaximum(game, session.catalog),
                    game.economy.plan.reserve, game.economy.savingsBalance, reward.sceneRes,
                    editing = rewardEditing, input = rewardInput, deposited = rewardDeposited),
            )
            return
        }
        if (rewardReceiptNeeded) {
            mutableState.value = mutableState.value.copy(loading = false, busy = false, message = message,
                canRetry = true, canSkipGame = false,
                presentation = mutableState.value.presentation?.copy(canPlay = false))
            return
        }
        if (pending != null) {
            mutableState.value = mutableState.value.copy(loading = false, busy = false, message = message, canRetry = true,
                canSkipGame = false,
                presentation = mutableState.value.presentation?.copy(canPlay = false))
            return
        }
        val game = latest ?: return
        if (leaveWhenLoaded) {
            leaveWhenLoaded = false
            leave()
            return
        }
        val occurrence = game.engine?.events?.find { it.id == occurrenceId }
        if (occurrence?.status in setOf(EventStatus.COMPLETED, EventStatus.PAUSED, EventStatus.CARRIED, EventStatus.CARRIED_ACTIVE)) {
            exit()
            return
        }
        val kind = occurrence?.let(::gameKind)
        if (occurrence?.status != EventStatus.ACTIVE || kind == null) {
            mutableState.value = DeedGameUiState(loading = false, message = "Это дело больше недоступно.")
            return
        }
        val event = session.catalog.content.events.single { it.id == occurrence.eventId }
        val reward = if (choiceId == null) session.catalog.content.choices.single { it.eventId == event.id }.moneyDelta else 0L
        val storyGame = choiceId != null
        val card = session.catalog.cards[event.id]
        val theme = card?.presentation?.media?.let { storyGameTheme(it, kind) }
        mutableState.value = DeedGameUiState(
            loading = false,
            type = when (kind) {
                DeedGameKind.MEMORY -> DeedGameType.MEMORY
                DeedGameKind.COMPARISON -> DeedGameType.COMPARISON
                DeedGameKind.PRECISION -> DeedGameType.PRECISION
                DeedGameKind.LIGHTS -> DeedGameType.LIGHTS
                DeedGameKind.SEQUENCE -> DeedGameType.SEQUENCE
                DeedGameKind.PIPES -> DeedGameType.PIPES
                DeedGameKind.DIFFERENCES -> DeedGameType.DIFFERENCES
                DeedGameKind.STACKING -> DeedGameType.STACKING
                DeedGameKind.TELESCOPE -> DeedGameType.TELESCOPE
                DeedGameKind.MARK -> DeedGameType.MARK
                DeedGameKind.WHEEL -> DeedGameType.WHEEL
                DeedGameKind.STRAP -> DeedGameType.STRAP
                DeedGameKind.SIGNAL -> DeedGameType.SIGNAL
                DeedGameKind.ROUTE_MAP -> DeedGameType.ROUTE_MAP
                DeedGameKind.BOAT -> DeedGameType.BOAT
                DeedGameKind.NEED_WANT -> DeedGameType.NEED_WANT
                DeedGameKind.REAL_DISCOUNT -> DeedGameType.REAL_DISCOUNT
                DeedGameKind.COIN_BALANCE -> DeedGameType.COIN_BALANCE
                DeedGameKind.FINANCIAL_TRAP -> DeedGameType.FINANCIAL_TRAP
                DeedGameKind.EXTRA_PURCHASE -> DeedGameType.EXTRA_PURCHASE
            },
            presentation = DeedGamePresentation(renderPetText(session.catalog.displayTitle(event), game.pet.name), reward, pending == null && message == null && !repairWriting,
                storyAction = storyGame,
                sceneRes = eventSceneBackground(event.id, card?.scene),
                instructions = theme?.instructions ?: storyGameInstructions(kind),
                activityArtworkRes = theme?.objectRes ?: eventSceneArtwork(event.id, card?.character)?.resource),
            message = message,
            canRetry = pending != null || rejectedAction != null || comparisonSaveFailed,
            audioOccurrenceId = occurrence.id,
            eventMedia = card?.presentation?.media ?: EventMedia(),
            canSkipGame = session.demoModeEnabled && rejectedAction == null && !repairWriting && !repairUnconfirmed(),
            demoMode = session.demoModeEnabled,
            repairCoins = EconomyOperations.depositAvailableBalance(game.economy).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }

    private fun gameKind(occurrence: EventOccurrence): DeedGameKind? {
        val policy = session.catalog.policies[occurrence.eventId] ?: return null
        return if (choiceId == null) policy.deedGameKind.takeIf { occurrence.origin == EventOrigin.DEED }
        else policy.choiceGameKinds[choiceId].takeIf { occurrence.origin == EventOrigin.SCHEDULE }
    }

    private companion object {
        val financialGameKinds = setOf(DeedGameKind.NEED_WANT, DeedGameKind.REAL_DISCOUNT,
            DeedGameKind.COIN_BALANCE, DeedGameKind.FINANCIAL_TRAP, DeedGameKind.EXTRA_PURCHASE)
        val chapterGameKinds = setOf(DeedGameKind.TELESCOPE, DeedGameKind.MARK, DeedGameKind.WHEEL,
            DeedGameKind.STRAP, DeedGameKind.SIGNAL, DeedGameKind.ROUTE_MAP, DeedGameKind.BOAT)
        const val REPAIR_KEY = "boat_payment_key"
        const val REPAIR_REQUEST = "boat_payment_request"
        const val REPAIR_CONFIRMED = "boat_payment_confirmed"
        const val REWARD_REQUEST = "deed_reward_request"
        const val REWARD_TRANSFER = "deed_reward_transfer"
        const val REWARD_EDITING = "deed_reward_editing"
        const val REWARD_INPUT = "deed_reward_input"
        const val REWARD_DISMISSED = "deed_reward_dismissed"
        const val REWARD_REJECTED = "deed_reward_rejected"
    }
}
