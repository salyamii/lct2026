package ru.nksk.lctapp.data.game

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.nksk.lctapp.data.game.local.MiniGameCompletionEntity
import ru.nksk.lctapp.data.game.local.GoalSelectionEntity
import ru.nksk.lctapp.data.game.local.CompletedGoalProjectEntity
import ru.nksk.lctapp.domain.engine.CompletedGoalProject
import ru.nksk.lctapp.data.game.local.CURRENT_GAME_ID
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.OwnedItemEntity
import ru.nksk.lctapp.data.game.local.PlayerDecisionEntity
import ru.nksk.lctapp.data.game.local.EngineDeedEntity
import ru.nksk.lctapp.data.game.local.DayJournalEntity
import ru.nksk.lctapp.data.game.local.EngineEventEntity
import ru.nksk.lctapp.data.game.local.toDomain
import ru.nksk.lctapp.data.game.local.toEntity
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

internal class RoomGameRepository @Inject constructor(private val database: GameDatabase) : GameRepository {
    private val dao = database.gameStateDao()

    override fun observe(): Flow<GameState?> = database.invalidationTracker
        .createFlow("GAME_STATE", "BUDGET_PLANNING", "OWNED_ITEM", "PLAYER_DECISION", "ENGINE_STATE", "ENGINE_EVENT", "ENGINE_DEED", "MINI_GAME_COMPLETION", "GOAL_SELECTION", "COMPLETED_GOAL_PROJECT", "DAY_JOURNAL")
        .map { read() }
        .distinctUntilChanged()

    override suspend fun read(): GameState? = database.withReadTransaction { readInTransaction() }

    override suspend fun initializeIfAbsent(initial: GameState): GameState = database.withWriteTransaction {
        readInTransaction() ?: run {
            dao.insertState(initial.toEntity())
            writeChildren(initial)
            database.onboardingDraftDao().clear()
            checkNotNull(readInTransaction())
        }
    }

    override suspend fun update(transform: (GameState) -> GameState): GameState = database.withWriteTransaction {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val next = transform(current)
        check(dao.updateState(next.toEntity()) == 1) { "Saved game disappeared during update" }
        dao.deleteBudgetPlanning(CURRENT_GAME_ID)
        dao.deleteMiniGameCompletions(CURRENT_GAME_ID)
        dao.deleteGoalSelection(CURRENT_GAME_ID)
        dao.deleteCompletedGoalProjects(CURRENT_GAME_ID)
        dao.deleteDecisions(CURRENT_GAME_ID)
        dao.deleteOwnedItems(CURRENT_GAME_ID)
        dao.deleteEngineEvents(CURRENT_GAME_ID)
        dao.deleteEngineDeeds(CURRENT_GAME_ID)
        dao.deleteDayJournal(CURRENT_GAME_ID)
        dao.deleteEngine(CURRENT_GAME_ID)
        writeChildren(next)
        checkNotNull(readInTransaction())
    }

    private suspend fun readInTransaction(): GameState? {
        val rows = dao.readStates()
        check(rows.size <= 1) { "Multiple saved games are not supported" }
        val row = rows.singleOrNull() ?: return null
        check(row.id == CURRENT_GAME_ID) { "Unknown saved game identity: ${row.id}" }
        return row.toDomain(dao.readDecisions(row.id), dao.readOwnedItems(row.id), dao.readBudgetPlanning(row.id)).copy(
            completedMiniGames = dao.readMiniGameCompletions(row.id).map { it.attemptId }.toSet(),
            selectedGoalId = dao.readGoalSelection(row.id)?.goalId,
            completedGoalProjects = dao.readCompletedGoalProjects(row.id).map { CompletedGoalProject(it.goalId, it.decisionId) },
            engine = dao.readEngine(row.id)?.toDomain(dao.readEngineEvents(row.id), dao.readEngineDeeds(row.id), dao.readDayJournal(row.id)),
        )
    }

    private suspend fun writeChildren(state: GameState) {
        state.economy.planning?.let { dao.insertBudgetPlanning(it.toEntity()) }
        state.selectedGoalId?.let { dao.insertGoalSelection(GoalSelectionEntity(CURRENT_GAME_ID, it)) }
        dao.insertMiniGameCompletions(state.completedMiniGames.map { MiniGameCompletionEntity(it, CURRENT_GAME_ID) })
        state.engine?.let { engine ->
            dao.insertEngine(engine.toEntity())
            dao.insertDayJournal(engine.journal.mapIndexed { position, entry ->
                DayJournalEntity(entry.id, CURRENT_GAME_ID, position, entry.kind.name, entry.sourceId, entry.moneyDelta, entry.energyDelta)
            })
            dao.insertEngineDeeds(engine.deeds.mapIndexed { position, offer ->
                EngineDeedEntity(offer.id, CURRENT_GAME_ID, position, offer.eventId, offer.expiresDay, offer.completed)
            })
            dao.insertEngineEvents(engine.events.mapIndexed { position, occurrence ->
                EngineEventEntity(occurrence.id, CURRENT_GAME_ID, position, occurrence.eventId.takeIf { occurrence.deedOfferId == null }, occurrence.status.name, occurrence.deedOfferId)
            })
        }
        dao.insertDecisions(state.story.decisions.mapIndexed { position, decision ->
            PlayerDecisionEntity(decision.id, CURRENT_GAME_ID, position, decision.choiceId)
        })
        dao.insertCompletedGoalProjects(state.completedGoalProjects.map { CompletedGoalProjectEntity(it.decisionId, it.goalId) })
        dao.insertOwnedItems(state.ownedItems.mapIndexed { position, item ->
            OwnedItemEntity(item.id, CURRENT_GAME_ID, position, item.itemId)
        })
    }
}
