package ru.nksk.lctapp.data.game

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.nksk.lctapp.data.game.local.CURRENT_GAME_ID
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.OwnedItemEntity
import ru.nksk.lctapp.data.game.local.PlayerDecisionEntity
import ru.nksk.lctapp.data.game.local.toDomain
import ru.nksk.lctapp.data.game.local.toEntity
import ru.nksk.lctapp.domain.game.GameRepository
import ru.nksk.lctapp.domain.game.GameState

internal class RoomGameRepository @Inject constructor(private val database: GameDatabase) : GameRepository {
    private val dao = database.gameStateDao()

    override fun observe(): Flow<GameState?> = database.invalidationTracker
        .createFlow("GAME_STATE", "OWNED_ITEM", "PLAYER_DECISION")
        .map { read() }
        .distinctUntilChanged()

    override suspend fun read(): GameState? = database.withReadTransaction { readInTransaction() }

    override suspend fun initializeIfAbsent(initial: GameState): GameState = database.withWriteTransaction {
        readInTransaction() ?: run {
            dao.insertState(initial.toEntity())
            writeChildren(initial)
            checkNotNull(readInTransaction())
        }
    }

    override suspend fun update(transform: (GameState) -> GameState): GameState = database.withWriteTransaction {
        val current = checkNotNull(readInTransaction()) { "Game has not been initialized" }
        val next = transform(current)
        check(dao.updateState(next.toEntity()) == 1) { "Saved game disappeared during update" }
        dao.deleteDecisions(CURRENT_GAME_ID)
        dao.deleteOwnedItems(CURRENT_GAME_ID)
        writeChildren(next)
        checkNotNull(readInTransaction())
    }

    private suspend fun readInTransaction(): GameState? {
        val rows = dao.readStates()
        check(rows.size <= 1) { "Multiple saved games are not supported" }
        val row = rows.singleOrNull() ?: return null
        check(row.id == CURRENT_GAME_ID) { "Unknown saved game identity: ${row.id}" }
        return row.toDomain(dao.readDecisions(row.id), dao.readOwnedItems(row.id))
    }

    private suspend fun writeChildren(state: GameState) {
        dao.insertDecisions(state.story.decisions.mapIndexed { position, decision ->
            PlayerDecisionEntity(decision.id, CURRENT_GAME_ID, position, decision.choiceId)
        })
        dao.insertOwnedItems(state.ownedItems.mapIndexed { position, item ->
            OwnedItemEntity(item.id, CURRENT_GAME_ID, position, item.itemId)
        })
    }
}
