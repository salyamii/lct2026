package ru.nksk.lctapp.domain.timemachine

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.nksk.lctapp.domain.analytics.*
import ru.nksk.lctapp.domain.content.EventType
import ru.nksk.lctapp.domain.engine.*
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.history.*
import ru.nksk.lctapp.domain.pet.renderPetText

/**
 * Other paths are available from the start, once a compatible decision exists in history.
 * Replay is pure transition on copies. Explicit learning writes never replace the game snapshot.
 */
class TimeMachine(
    private val games: GameRepository,
    private val engine: GameEngine,
    private val catalog: GameCatalog,
    private val fingerprint: String = GameCatalogFingerprint.compute(catalog),
) {
    private val simulations = ConcurrentHashMap<String, VerifiedSimulation>()
    private val attempts = ConcurrentHashMap<String, VerifiedSimulation>()
    private val quizzes = ConcurrentHashMap<String, VerifiedQuiz>()
    private val submissions = Mutex()

    suspend fun availableDecisions(): TimeMachineAvailability = availability(::decision)

    /** End-of-day reflections revisit optional spending or paid help that had a feasible free alternative. */
    suspend fun availableReflections(): TimeMachineAvailability = availability(::reflection)

    private suspend fun availability(select: (AuditEntry) -> TimeMachineDecision?): TimeMachineAvailability {
        val snapshot = readCoherent() ?: return TimeMachineAvailability(TimeMachineStatus.UNAVAILABLE, reason = "Сохранение обновилось. Открой машину времени ещё раз.")
        val decisions = withContext(Dispatchers.Default) {
            snapshot.history.filter { it.type == AuditType.COMMAND && compatible(it) }
                .mapNotNull { entry -> select(entry)?.takeIf { it.alternatives.isNotEmpty() } }
        }
        return TimeMachineAvailability(TimeMachineStatus.READY, decisions,
            if (decisions.isEmpty()) "Пока нет сохранённых решений с доступными альтернативами." else null)
    }

    suspend fun simulate(request: TimeMachineRequest): TimeMachineResult {
        val snapshot = readCoherent() ?: return unavailable(request, "Нет согласованной истории для пересчёта.")
        val target = snapshot.history.firstOrNull { it.id == request.entryId && it.type == AuditType.COMMAND }
            ?: return unavailable(request, "Это решение отсутствует в истории текущей игры.")
        if (target.contentFingerprint == null) return unavailable(request, "Для этого решения не сохранена версия содержимого игры.")
        if (!compatible(target)) return TimeMachineResult(TimeMachineStatus.INCOMPATIBLE_VERSION, request, reason = "Версия правил или содержимого изменилась.")
        val alternative = decision(target)?.alternatives?.firstOrNull { it.id == request.alternativeId }
            ?: return unavailable(request, "Этот вариант не был доступен в момент решения.")
        val through = request.throughSequence ?: target.sequence
        if (through < target.sequence || snapshot.history.none { it.sequence == through }) return unavailable(request, "Граница сравнения отсутствует в сохранённой истории.")
        val records = snapshot.history.filter { it.sequence in target.sequence..through }
        val result = withContext(Dispatchers.Default) { replay(request, target, alternative, records, through, snapshot.history) }
        val verified = VerifiedSimulation(result, target, alternative, withContext(Dispatchers.Default) {
            HistorySourceGuard.capture(target.runId, target.sequence, through, records)
        })
        result.simulationId?.let { attempts[it] = verified }
        if (result.status in setOf(TimeMachineStatus.COMPLETE, TimeMachineStatus.DIVERGED) &&
            result.simulationId != null && result.baseline != null && result.alternative != null && result.reachedSequence!! >= target.sequence) {
            simulations[result.simulationId] = verified
        }
        return result
    }

    /** Returns a verified, versioned question. An unavailable/empty comparison produces no invented quiz. */
    fun quiz(simulationId: String, kind: TimeMachineQuizKind): TimeMachineQuiz? {
        val simulation = simulations[simulationId] ?: return null
        val prepared = when (kind) {
            TimeMachineQuizKind.LEDGER -> ledgerQuiz(simulation)
            TimeMachineQuizKind.CAUSE -> causeQuiz(simulation)
        } ?: return null
        quizzes[prepared.public.id] = prepared
        return prepared.public
    }

    /** Submission identity and displayed evidence must both be reused after an uncertain save. */
    suspend fun submitQuiz(quizId: String, optionId: String, submissionId: String, usedHint: Boolean = false,
        questionPresented: Boolean = false): TimeMachineQuizAnswer = submissions.withLock {
        require(submissionId.isNotBlank())
        val quiz = requireNotNull(quizzes[quizId]) { "Reopen this comparison before answering" }
        require(quiz.public.options.any { it.id == optionId }) { "Unknown quiz option" }
        val snapshot = requireNotNull(readCoherent()) { "Game history changed; reopen the quiz" }
        val simulation = requireNotNull(simulations[quiz.public.simulationId])
        validateSimulationSource(snapshot, simulation)
        val allFacts = snapshot.history.flatMap { it.facts }
        val eventId = "time-machine-answer:$submissionId"
        val old = allFacts.firstOrNull { it.eventId == eventId }
        if (old != null) {
            val answer = requireNotNull(old.detail as? FactDetail.QuestionAnswer) { "Conflicting quiz submission" }
            require(answer.questionId == quizId && old.actionId == submissionId && answer.task == quiz.task(optionId)) { "Conflicting quiz submission" }
            return@withLock TimeMachineQuizAnswer(quizId, optionId, answer.task.isCorrect(), quiz.explanation, answer.attempt, true, submissionId)
        }
        val previous = allFacts.filter { (it.detail as? FactDetail.QuestionAnswer)?.questionId == quizId }
        val attempt = Math.addExact(previous.maxOfOrNull { (it.detail as FactDetail.QuestionAnswer).attempt } ?: 0, 1)
        val revealed = quiz.public.answerAlreadyShown || previous.isNotEmpty()
        val assistance = buildSet {
            if (usedHint) add(Assistance.HINT)
            if (revealed) add(Assistance.ANSWER_REVEALED)
        }
        val fact = AnalyticsFact(
            eventId = eventId, gameRunId = quiz.runId, episodeId = quiz.episodeId, actionId = submissionId,
            sequence = Math.addExact(snapshot.history.lastOrNull()?.sequence ?: 0L, 1L),
            detail = FactDetail.QuestionAnswer(quizId, attempt, quiz.task(optionId),
                answerWasRevealed = revealed, seriesClosed = true,
                comparisonFamily = quiz.comparisonFamily, simulationId = quiz.public.simulationId),
            context = DecisionContext(presentationId = quizId, informationPresented = questionPresented, complete = questionPresented,
                assistance = assistance, day = snapshot.state.engine?.day),
            learningContext = LearningContext.COUNTERFACTUAL, contextFamily = "time_machine:${quiz.public.kind.name.lowercase()}",
            contentVersion = fingerprint, gameRulesVersion = catalog.rules.id,
        )
        games.recordFacts(listOf(fact), simulation.sourceGuard)
        TimeMachineQuizAnswer(quizId, optionId, (fact.detail as FactDetail.QuestionAnswer).task.isCorrect(), quiz.explanation, attempt,
            submissionId = submissionId)
    }

    /** Called for an explicitly requested calculation, not when rendering/observing game state. */
    suspend fun recordSimulationLifecycle(simulationId: String) = submissions.withLock {
        val simulation = requireNotNull(attempts[simulationId]) { "No verified calculation" }
        val snapshot = lifecycleSnapshot(simulation)
        val terminal = when (simulation.result.status) {
            TimeMachineStatus.COMPLETE -> TimeMachineLifecycleEvent.SIMULATION_COMPLETED
            TimeMachineStatus.DIVERGED -> TimeMachineLifecycleEvent.SIMULATION_DIVERGED
            else -> TimeMachineLifecycleEvent.SIMULATION_UNAVAILABLE
        }
        recordLifecycle(snapshot, simulation, listOf(
            lifecycleFact(snapshot, simulation, TimeMachineLifecycleEvent.SIMULATION_STARTED),
            lifecycleFact(snapshot, simulation, terminal),
        ))
    }

    /** Generation is not a presentation. The screen reports an actually visible question separately. */
    suspend fun recordQuestionPresented(quizId: String) = submissions.withLock {
        val quiz = requireNotNull(quizzes[quizId]) { "Reopen this comparison" }
        val simulation = requireNotNull(simulations[quiz.public.simulationId])
        val snapshot = lifecycleSnapshot(simulation)
        recordLifecycle(snapshot, simulation, listOf(lifecycleFact(snapshot, simulation,
            TimeMachineLifecycleEvent.QUESTION_PRESENTED, questionId = quizId)))
    }

    suspend fun recordExplanationShown(quizId: String, submissionId: String) = submissions.withLock {
        val quiz = requireNotNull(quizzes[quizId]) { "Reopen this comparison" }
        val simulation = requireNotNull(simulations[quiz.public.simulationId])
        val snapshot = lifecycleSnapshot(simulation)
        val answer = requireNotNull(snapshot.history.flatMap { it.facts }.firstOrNull { it.eventId == "time-machine-answer:$submissionId" }
            ?.detail as? FactDetail.QuestionAnswer) { "This explanation has no accepted answer" }
        require(answer.questionId == quizId) { "This answer belongs to another question" }
        recordLifecycle(snapshot, simulation, listOf(lifecycleFact(snapshot, simulation,
            TimeMachineLifecycleEvent.EXPLANATION_SHOWN, questionId = quizId,
            submissionId = submissionId, attempt = answer.attempt)))
    }

    private suspend fun lifecycleSnapshot(simulation: VerifiedSimulation): ReadSnapshot {
        val snapshot = requireNotNull(readCoherent()) { "The current history is unavailable" }
        validateSimulationSource(snapshot, simulation)
        return snapshot
    }

    private suspend fun validateSimulationSource(snapshot: ReadSnapshot, simulation: VerifiedSimulation) {
        withContext(Dispatchers.Default) { simulation.sourceGuard.requireMatches(snapshot.history) }
    }

    private fun lifecycleFact(snapshot: ReadSnapshot, simulation: VerifiedSimulation, event: TimeMachineLifecycleEvent,
        questionId: String? = null, submissionId: String? = null, attempt: Int? = null): AnalyticsFact {
        val result = simulation.result
        val id = requireNotNull(result.simulationId)
        val presentation = event == TimeMachineLifecycleEvent.QUESTION_PRESENTED || event == TimeMachineLifecycleEvent.EXPLANATION_SHOWN
        return AnalyticsFact(
            eventId = "time-machine:${event.name.lowercase()}:${submissionId ?: questionId ?: id}",
            gameRunId = simulation.target.runId, episodeId = "time-machine:${simulation.target.id}",
            actionId = submissionId ?: questionId ?: id,
            sequence = Math.addExact(snapshot.history.lastOrNull()?.sequence ?: 0L, 1L),
            detail = FactDetail.TimeMachineLifecycle(event, id, requireNotNull(result.resultHash), simulation.target.id,
                requireNotNull(result.requestedSequence), requireNotNull(result.reachedSequence), result.status.name,
                questionId, submissionId, attempt),
            context = DecisionContext(presentationId = if (presentation) questionId else null,
                informationPresented = presentation, complete = presentation, day = simulation.target.before?.engine?.day),
            mode = if (presentation) AnalyticsMode.REAL else AnalyticsMode.SIMULATION,
            actor = AnalyticsActor.SYSTEM, learningContext = LearningContext.COUNTERFACTUAL,
            contextFamily = "time_machine:lifecycle", contentVersion = fingerprint, gameRulesVersion = catalog.rules.id,
        )
    }

    private suspend fun recordLifecycle(snapshot: ReadSnapshot, simulation: VerifiedSimulation, facts: List<AnalyticsFact>) {
        val existing = snapshot.history.flatMap { it.facts }.associateBy { it.eventId }
        val fresh = facts.filter { fact ->
            val previous = existing[fact.eventId]
            if (previous != null) require(previous == fact.copy(sequence = previous.sequence)) { "Conflicting lifecycle fact" }
            previous == null
        }
        // The repository rechecks the run and assigns the final sequence inside its write transaction.
        if (fresh.isNotEmpty()) games.recordFacts(fresh, simulation.sourceGuard)
    }

    private fun replay(request: TimeMachineRequest, target: AuditEntry, alternative: TimeMachineAlternative,
        records: List<AuditEntry>, through: Long, history: List<AuditEntry>): TimeMachineResult {
        var baseline = requireNotNull(target.before)
        var branch = baseline
        var reached = target.sequence - 1
        val originalOperations = mutableListOf<LedgerEntry>()
        val changedOperations = mutableListOf<LedgerEntry>()
        fun result(status: TimeMachineStatus, reason: String? = null): TimeMachineResult {
            val hash = HistoryCodec.sha256(listOf(target.runId, request.entryId, request.alternativeId, through, reached, status,
                fingerprint, HistoryCodec.encodeState(baseline), HistoryCodec.encodeState(branch),
                originalOperations.toString(), changedOperations.toString()).joinToString("\n"))
            return TimeMachineResult(status, request, "simulation:$hash", hash, target.runId, reached, through,
                TimeMachineBranch(baseline, originalOperations.toList()), TimeMachineBranch(branch, changedOperations.toList()), reason)
        }
        for (entry in records) {
            if (entry.runId != target.runId || entry.formatVersion != HISTORY_FORMAT_VERSION) return result(TimeMachineStatus.INCOMPATIBLE_VERSION, "История относится к другой версии или игре.")
            if (entry.type == AuditType.FACTS || entry.type == AuditType.REJECTED) { reached = entry.sequence; continue }
            val before = entry.before ?: return result(TimeMachineStatus.DIVERGED, "На этом месте начинается другая сохранённая история.")
            val after = entry.after ?: return result(TimeMachineStatus.UNAVAILABLE, "Не сохранён результат действия.")
            if (!sameState(baseline, before)) return result(TimeMachineStatus.UNAVAILABLE, "В истории не хватает промежуточного состояния.")
            if (entry.type == AuditType.TECHNICAL_UPDATE) {
                if (!sameState(before, after.copy(locationScene = before.locationScene))) return result(TimeMachineStatus.DIVERGED, "Следующее изменение нельзя достоверно повторить в другом варианте.")
                branch = branch.copy(locationScene = after.locationScene)
                baseline = after
                reached = entry.sequence
                continue
            }
            if (entry.type != AuditType.COMMAND || !compatible(entry)) return result(TimeMachineStatus.INCOMPATIBLE_VERSION, "Для следующего действия нет совместимой версии правил.")
            val originalRequest = requireNotNull(entry.request)
            val replayed = runCatching { engine.transition(before, originalRequest,
                history.takeWhile { it.sequence < entry.sequence }) }.getOrNull()
                ?: return result(TimeMachineStatus.INCOMPATIBLE_VERSION, "Исходное действие больше не воспроизводится по сохранённым правилам.")
            if (!sameState(replayed, after)) return result(TimeMachineStatus.INCOMPATIBLE_VERSION, "Исходный результат не совпал с сохранённой историей.")
            val historicalOperations = runCatching { CanonicalLedger.fromTransition(before, after, originalRequest) }.getOrNull()
                ?: return result(TimeMachineStatus.UNAVAILABLE, "Не удалось восстановить точные денежные операции.")
            if (historicalOperations != entry.operations) return result(TimeMachineStatus.UNAVAILABLE, "Сохранённые операции не совпадают с результатом действия.")
            val command = if (entry.id == target.id) alternative.command else originalRequest.command
            if (command is EngineCommand.RequestFinancialPractice &&
                command.kind == ru.nksk.lctapp.domain.finance.FinancialQuestionKind.PLAN_REVIEW) {
                return result(TimeMachineStatus.DIVERGED,
                    "После изменения решения нужен новый разбор плана и факта. Старый ответ нельзя перенести в этот вариант; сравнение показано до разбора.")
            }
            if (command is EngineCommand.BeginDay && entry.id != target.id && catalog.plan(branch) != command.eventIds) {
                return result(TimeMachineStatus.DIVERGED, "После этого выбора план следующего дня был бы другим.")
            }
            if (command is EngineCommand.CompleteDeed && !sameDeed(before, branch, command.occurrenceId)) {
                return result(TimeMachineStatus.DIVERGED, "Результат прежней мини-игры не подходит к другому делу.")
            }
            if (command is EngineCommand.CompleteStoryGame && !sameStoryGame(before, branch, command)) {
                return result(TimeMachineStatus.DIVERGED, "Результат прежней мини-игры не подходит к другому действию истории.")
            }
            val changedRequest = originalRequest.copy(expectedRevision = branch.engine?.revision, command = command ?: originalRequest.command, context = null)
            val next = if (command == null) branch else runCatching {
                if (entry.id == target.id && alternative.assumesCompletedWork) engine.simulateCompletedWork(branch, changedRequest)
                else engine.transition(branch, changedRequest)
            }.getOrNull()
                ?: return result(TimeMachineStatus.DIVERGED, "Следующее действие уже недоступно: изменились деньги, силы или условия события.")
            val operations = if (command == null) emptyList() else runCatching { CanonicalLedger.fromTransition(branch, next, changedRequest) }.getOrNull()
                ?: return result(TimeMachineStatus.DIVERGED, "В новом варианте не удалось проверить денежный результат.")
            baseline = after
            branch = next
            originalOperations += historicalOperations
            changedOperations += operations
            reached = entry.sequence
        }
        return result(TimeMachineStatus.COMPLETE)
    }

    private fun decision(entry: AuditEntry): TimeMachineDecision? {
        val before = entry.before ?: return null
        val request = entry.request ?: return null
        val command = request.command
        val choices: List<TimeMachineAlternative>
        val title: String
        when (command) {
            is EngineCommand.Choose, is EngineCommand.CompleteEvent, is EngineCommand.CompleteStoryGame -> {
                val occurrence = before.engine?.currentEvent ?: return null
                val event = catalog.content.events.firstOrNull { it.id == occurrence.eventId } ?: return null
                val selected = when (command) {
                    is EngineCommand.Choose -> command.choiceId
                    is EngineCommand.CompleteEvent -> command.choiceId
                    is EngineCommand.CompleteStoryGame -> command.choiceId
                    else -> error("Not an event choice")
                }
                val policy = catalog.policies[event.id]
                val all = catalog.content.choices.filter {
                    it.eventId == event.id && it.id !in policy?.disabledChoiceIds.orEmpty()
                }.sortedBy { it.position }
                if (all.none { it.moneyDelta != 0L } && event.moneyDeltaOnStart == 0L && all.map { policy?.energyFor(it.id) }.distinct().size <= 1) return null
                choices = all.filter { it.id != selected }.map { option ->
                    val work = option.id in policy?.choiceGameKinds.orEmpty()
                    val action = catalog.displayAction(option)
                    val label = if (action != option.text && option.moneyDelta < 0)
                        "$action · ${Math.negateExact(option.moneyDelta)} монет" else action
                    TimeMachineAlternative("choice:${option.id}", if (work) "$label — если закончить работу" else label,
                        if (command is EngineCommand.Choose && !work) EngineCommand.Choose(occurrence.id, option.id) else EngineCommand.CompleteEvent(occurrence.id, option.id),
                        assumesCompletedWork = work)
                }
                title = catalog.displayTitle(event)
            }
            is EngineCommand.Feed -> {
                choices = catalog.meals.filter { it.id != command.mealId }.map { TimeMachineAlternative("meal:${it.id}", "Другой обед: ${it.price} монет", EngineCommand.Feed(it.id)) }
                title = "Питание"
            }
            is EngineCommand.DepositSavings -> { choices = listOf(TimeMachineAlternative("skip", "Пока не откладывать эти монеты", null)); title = "Пополнение накоплений" }
            is EngineCommand.WithdrawSavings -> { choices = listOf(TimeMachineAlternative("skip", "Оставить монеты в накоплениях", null)); title = "Снятие накоплений" }
            is EngineCommand.BuyGoalItem -> { choices = listOf(TimeMachineAlternative("skip", "Купить эту часть позже", null)); title = catalog.content.items.firstOrNull { it.id == command.itemId }?.name ?: "Часть цели" }
            else -> return null
        }
        val feasible = choices.filter { option -> option.command == null || runCatching {
            val preview = request.copy(command = option.command, expectedRevision = before.engine?.revision, context = null)
            if (option.assumesCompletedWork) engine.simulateCompletedWork(before, preview) else engine.transition(before, preview)
        }.isSuccess }
        return TimeMachineDecision(entry.id, entry.sequence, before.engine?.day, title, feasible)
    }

    private fun reflection(entry: AuditEntry): TimeMachineDecision? {
        val selectedId = entry.request?.command?.eventChoiceId() ?: return null
        if (entry.operations.none { it.kind == LedgerKind.AVAILABLE_EXPENSE && it.amount > 0 }) return null
        val eventId = entry.before?.engine?.currentEvent?.eventId ?: return null
        val event = catalog.content.events.firstOrNull { it.id == eventId } ?: return null
        if (event.type !in setOf(EventType.WANT, EventType.RANDOM, EventType.STORY)) return null
        val selected = catalog.content.choices.firstOrNull { it.id == selectedId && it.eventId == eventId }
            ?: return null
        val policy = catalog.policies[eventId]
        if (selected.moneyDelta >= 0 || selectedId in policy?.feedsPetChoiceIds.orEmpty()) return null
        val candidate = decision(entry) ?: return null
        val alternatives = candidate.alternatives.filter { alternative ->
            val choiceId = alternative.command?.eventChoiceId() ?: return@filter false
            val choice = catalog.content.choices.firstOrNull { it.id == choiceId && it.eventId == eventId }
                ?: return@filter false
            if (choice.moneyDelta != 0L || choiceId in policy?.feedsPetChoiceIds.orEmpty()) return@filter false
            when (event.type) {
                EventType.WANT -> true
                EventType.RANDOM -> policy != null && policy.energyFor(choiceId) > 0 &&
                    (choiceId in policy.choiceGameKinds || choiceId in policy.choiceEnergyCosts)
                EventType.STORY -> policy != null && policy.energyFor(choiceId) > 0 &&
                    choiceId in policy.choiceGameKinds
                else -> false
            }
        }
        return candidate.copy(alternatives = alternatives).takeIf { alternatives.isNotEmpty() }
    }

    private fun EngineCommand.eventChoiceId(): String? = when (this) {
        is EngineCommand.Choose -> choiceId
        is EngineCommand.CompleteEvent -> choiceId
        is EngineCommand.CompleteStoryGame -> choiceId
        else -> null
    }

    private fun compatible(entry: AuditEntry) = entry.contentFingerprint == fingerprint &&
        entry.before?.engine?.rulesId.let { it == null || it == catalog.rules.id } && entry.formatVersion == HISTORY_FORMAT_VERSION

    private fun sameDeed(original: GameState, alternative: GameState, id: String): Boolean {
        val first = original.engine?.events?.firstOrNull { it.id == id } ?: return false
        val second = alternative.engine?.events?.firstOrNull { it.id == id } ?: return false
        return first.eventId == second.eventId && first.deedOfferId == second.deedOfferId && first.origin == second.origin &&
            catalog.policies[first.eventId]?.deedGameKind == catalog.policies[second.eventId]?.deedGameKind
    }

    private fun sameStoryGame(original: GameState, alternative: GameState, command: EngineCommand.CompleteStoryGame): Boolean {
        val first = original.engine?.events?.firstOrNull { it.id == command.occurrenceId } ?: return false
        val second = alternative.engine?.events?.firstOrNull { it.id == command.occurrenceId } ?: return false
        return first.eventId == second.eventId && first.origin == second.origin &&
            catalog.policies[first.eventId]?.choiceGameKinds?.get(command.choiceId) == command.score.kind &&
            catalog.policies[second.eventId]?.choiceGameKinds?.get(command.choiceId) == command.score.kind
    }

    private fun sameState(a: GameState, b: GameState) = HistoryCodec.encodeState(a) == HistoryCodec.encodeState(b)
    private suspend fun readCoherent(): ReadSnapshot? {
        val state = games.read() ?: return null
        val history = games.readHistory()
        val latest = history.lastOrNull { it.after != null }?.after
        if (latest != null && !sameState(state, latest)) return null
        if (history.zipWithNext().any { (a, b) -> a.sequence >= b.sequence || a.runId != b.runId }) return null
        return ReadSnapshot(state, history)
    }
    private fun unavailable(request: TimeMachineRequest, reason: String) = TimeMachineResult(TimeMachineStatus.UNAVAILABLE, request, reason = reason)
    private data class ReadSnapshot(val state: GameState, val history: List<AuditEntry>)
    private data class VerifiedSimulation(val result: TimeMachineResult, val target: AuditEntry,
        val alternative: TimeMachineAlternative, val sourceGuard: HistorySourceGuard)
    private data class VerifiedQuiz(
        val public: TimeMachineQuiz,
        val runId: String,
        val episodeId: String,
        val comparisonFamily: String?,
        val explanation: String,
        val task: (String) -> AssessmentTask,
    )

    private fun ledgerQuiz(simulation: VerifiedSimulation): VerifiedQuiz? {
        val result = simulation.result
        val branch = result.alternative ?: return null
        val initial = simulation.target.before ?: return null
        if (branch.operations.isEmpty() && result.baseline?.operations.isNullOrEmpty()) return null
        val task = AssessmentTask.ReadLedger(initial.economy.availableBalance, initial.economy.savingsBalance,
            branch.operations, LedgerQuestion.EXPENSE, 0)
        val expected = task.expectedAnswer()
        val wrong = linkedSetOf<Long>()
        wrong += if (expected == 0L) 1 else 0
        if (expected < Long.MAX_VALUE) wrong += expected + 1
        else wrong += expected - 1
        val options = (listOf(expected) + wrong.filter { it != expected }).distinct().map { TimeMachineQuizOption("coins:$it", reflectionCoins(it, singular = "монета")) }
            .sortedBy { HistoryCodec.sha256("${result.resultHash}:${it.id}") }
        val id = "time-quiz:${HistoryCodec.sha256("${result.resultHash}:ledger:1") }"
        val action = alternativeAction(simulation, initial.pet.name)
        val event = if (simulation.target.request?.command?.eventChoiceId() != null)
            catalog.content.events.firstOrNull { it.id == initial.engine?.currentEvent?.eventId } else null
        val context = event?.let { "В истории «${renderPetText(catalog.displayTitle(it), initial.pet.name)}» выбираем «$action»." }
            ?: "Представим, что выбрали «$action»."
        val public = TimeMachineQuiz(id, requireNotNull(result.simulationId), requireNotNull(result.resultHash), TimeMachineQuizKind.LEDGER,
            "$context Сколько монет потратим всего? Считай и покупки, которые случились дальше в сравнении.",
            options, answerAlreadyShown = true)
        val explanation = buildString {
            append("В этом варианте на все покупки и услуги потратили ${reflectionCoins(expected)}.")
            if (branch.deposited > 0) append(" В копилку положили ещё ${reflectionCoins(branch.deposited)}, но эти монеты всё ещё наши. Их не считаем потраченными.")
            if (branch.withdrawn > 0) append(" Из копилки достали ${reflectionCoins(branch.withdrawn)}. Просто достать монеты — ещё не значит потратить их.")
        }
        return VerifiedQuiz(public, requireNotNull(result.runId), "time-machine:${simulation.target.id}:ledger", null,
            explanation) { option ->
            task.copy(answer = option.removePrefix("coins:").toLong())
        }
    }

    private fun causeQuiz(simulation: VerifiedSimulation): VerifiedQuiz? {
        val result = simulation.result
        val original = result.baseline ?: return null
        val alternative = result.alternative ?: return null
        if (original.income != alternative.income || original.deposited != alternative.deposited ||
            original.withdrawn != alternative.withdrawn || original.spent <= alternative.spent) return null
        val reflection = reflection(simulation.target) ?: return null
        if (reflection.alternatives.none { it.id == simulation.alternative.id }) return null
        val before = simulation.target.before ?: return null
        val after = simulation.target.after ?: return null
        val command = simulation.alternative.command ?: return null
        val request = simulation.target.request?.copy(
            command = command, expectedRevision = before.engine?.revision, context = null,
        ) ?: return null
        val alternateOutcome = runCatching {
            if (simulation.alternative.assumesCompletedWork) engine.simulateCompletedWork(before, request)
            else engine.transition(before, request)
        }.getOrNull() ?: return null
        val alternateOperations = runCatching { CanonicalLedger.fromTransition(before, alternateOutcome, request) }
            .getOrNull() ?: return null
        val paid = TimeMachineBranch(after, simulation.target.operations).spent
        val wouldPay = TimeMachineBranch(alternateOutcome, alternateOperations).spent
        val saved = original.spent - alternative.spent
        // Only explain a verified difference that this one free alternative entirely accounts for.
        if (paid <= 0 || wouldPay != 0L || saved != paid) return null
        val eventId = before.engine?.currentEvent?.eventId ?: return null
        val event = catalog.content.events.firstOrNull { it.id == eventId } ?: return null
        val spentEnergy = (before.engine?.energy ?: return null) -
            (alternateOutcome.engine?.energy ?: return null)
        val work = event.type != EventType.WANT
        if (work && spentEnergy <= 0) return null
        val amount = reflectionCoins(saved)
        val action = alternativeAction(simulation, before.pet.name)
        val item = if (work) null else purchasedItemName(before, after, alternateOutcome)
        val effort = if (spentEnergy == 1) "немного устанем" else "устанем"
        val correctId = "spent_less"
        val options = if (work) listOf(
            TimeMachineQuizOption(correctId, "Сделаем сами: сохраним $amount, но $effort"),
            TimeMachineQuizOption("paid_for_help", "Сделаем сами и всё равно заплатим $amount"),
            TimeMachineQuizOption("no_work_needed", "Сохраним $amount и совсем не устанем"),
        ) else if (item != null) listOf(
            TimeMachineQuizOption(correctId, "Сохраним $amount, но «$item» не получим"),
            TimeMachineQuizOption("paid_anyway", "Потратим $amount и получим «$item»"),
            TimeMachineQuizOption("paid_for_refusal", "Потратим $amount, но «$item» не получим"),
        ) else listOf(
            TimeMachineQuizOption(correctId, "Не будем платить и сохраним $amount"),
            TimeMachineQuizOption("paid_anyway", "Заплатим $amount, как в нашей истории"),
            TimeMachineQuizOption("paid_for_refusal", "Откажемся, но всё равно потратим $amount"),
        )
        val orderedOptions = options.sortedBy { HistoryCodec.sha256("${result.resultHash}:${it.id}") }
        val id = "time-quiz:${HistoryCodec.sha256("${result.resultHash}:cause:2") }"
        val actual = if (item != null) "Мы купили «$item» за $amount."
            else "«${renderPetText(catalog.displayTitle(event), before.pet.name)}»: мы потратили $amount."
        val public = TimeMachineQuiz(id, requireNotNull(result.simulationId), requireNotNull(result.resultHash), TimeMachineQuizKind.CAUSE,
            "$actual А если выбрать «$action», что изменится?",
            orderedOptions, answerAlreadyShown = true)
        val explanation = if (work) {
            "При выборе «$action» сделаем работу сами и $effort. Платить за это действие не нужно: сохраним $amount."
        } else if (item != null) {
            "В нашей истории мы потратили $amount на «$item». При выборе «$action» не покупаем этот предмет, поэтому сохраняем $amount."
        } else {
            "В нашей истории мы заплатили $amount. При выборе «$action» платить не нужно, поэтому сохраняем $amount."
        }
        val comparisonFamily = simulation.target.facts.firstOrNull {
            it.detail is FactDetail.OptionalPurchase || it.detail is FactDetail.ResourceChoice || it.detail is FactDetail.SavingMovement
        }?.contextFamily?.takeUnless { it == "unspecified" }
        return VerifiedQuiz(public, requireNotNull(result.runId), "time-machine:${simulation.target.id}:cause", comparisonFamily, explanation) { option ->
            AssessmentTask.ExplainCause(correctId, option, listOf(simulation.target.id))
        }
    }

    private fun alternativeAction(simulation: VerifiedSimulation, petName: String): String {
        val choiceId = simulation.alternative.command?.eventChoiceId()
        val template = catalog.content.choices.firstOrNull { it.id == choiceId }?.let { catalog.displayAction(it) }
            ?.substringBefore(" · ") ?: simulation.alternative.title
        return renderPetText(template, petName)
    }

    /** Name only a verified purchase; experiences and shared rewards must not become invented items. */
    private fun purchasedItemName(before: GameState, after: GameState, alternative: GameState): String? {
        val original = before.ownedItems.groupingBy { it.itemId }.eachCount()
        val other = alternative.ownedItems.groupingBy { it.itemId }.eachCount()
        val added = after.ownedItems.groupingBy { it.itemId }.eachCount().filter { (id, count) ->
            count > original.getOrDefault(id, 0) && count > other.getOrDefault(id, 0)
        }
        val itemId = added.keys.singleOrNull() ?: return null
        return catalog.content.items.firstOrNull { it.id == itemId }?.name?.let { renderPetText(it, before.pet.name) }
    }

    private fun reflectionCoins(amount: Long, singular: String = "монету"): String = "$amount ${when {
        amount % 100 in 11L..14L -> "монет"
        amount % 10 == 1L -> singular
        amount % 10 in 2L..4L -> "монеты"
        else -> "монет"
    }}"
}
