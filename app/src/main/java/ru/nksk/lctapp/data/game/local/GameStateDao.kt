package ru.nksk.lctapp.data.game.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

/** No REPLACE or parent deletion: references and unrelated rows must survive writes. */
@Dao
internal interface GameStateDao {
    @Query("SELECT * FROM DAY_JOURNAL WHERE game_state_id = :gameId ORDER BY position")
    suspend fun readDayJournal(gameId: String): List<DayJournalEntity>

    @Insert suspend fun insertDayJournal(rows: List<DayJournalEntity>)

    @Query("DELETE FROM DAY_JOURNAL WHERE game_state_id = :gameId")
    suspend fun deleteDayJournal(gameId: String)

    @Query("SELECT project.* FROM COMPLETED_GOAL_PROJECT project INNER JOIN PLAYER_DECISION decision ON decision.id = project.decision_id WHERE decision.game_state_id = :gameId ORDER BY decision.position")
    suspend fun readCompletedGoalProjects(gameId: String): List<CompletedGoalProjectEntity>

    @Insert suspend fun insertCompletedGoalProjects(rows: List<CompletedGoalProjectEntity>)

    @Query("DELETE FROM COMPLETED_GOAL_PROJECT WHERE decision_id IN (SELECT id FROM PLAYER_DECISION WHERE game_state_id = :gameId)")
    suspend fun deleteCompletedGoalProjects(gameId: String)

    @Query("SELECT * FROM GOAL_SELECTION WHERE game_state_id = :gameId")
    suspend fun readGoalSelection(gameId: String): GoalSelectionEntity?

    @Insert suspend fun insertGoalSelection(row: GoalSelectionEntity)

    @Query("DELETE FROM GOAL_SELECTION WHERE game_state_id = :gameId")
    suspend fun deleteGoalSelection(gameId: String)

    @Query("SELECT * FROM MINI_GAME_COMPLETION WHERE game_state_id = :gameId")
    suspend fun readMiniGameCompletions(gameId: String): List<MiniGameCompletionEntity>

    @Insert suspend fun insertMiniGameCompletions(rows: List<MiniGameCompletionEntity>)

    @Query("DELETE FROM MINI_GAME_COMPLETION WHERE game_state_id = :gameId")
    suspend fun deleteMiniGameCompletions(gameId: String)

    @Query("SELECT * FROM ENGINE_STATE WHERE game_state_id = :gameId")
    suspend fun readEngine(gameId: String): EngineStateEntity?

    @Query("SELECT * FROM ENGINE_EVENT WHERE game_state_id = :gameId ORDER BY position")
    suspend fun readEngineEvents(gameId: String): List<EngineEventEntity>

    @Query("SELECT * FROM ENGINE_DEED WHERE game_state_id = :gameId ORDER BY position")
    suspend fun readEngineDeeds(gameId: String): List<EngineDeedEntity>

    @Insert suspend fun insertEngine(row: EngineStateEntity)
    @Insert suspend fun insertEngineEvents(rows: List<EngineEventEntity>)
    @Insert suspend fun insertEngineDeeds(rows: List<EngineDeedEntity>)

    @Query("DELETE FROM ENGINE_EVENT WHERE game_state_id = :gameId")
    suspend fun deleteEngineEvents(gameId: String)

    @Query("DELETE FROM ENGINE_DEED WHERE game_state_id = :gameId")
    suspend fun deleteEngineDeeds(gameId: String)

    @Query("DELETE FROM ENGINE_STATE WHERE game_state_id = :gameId")
    suspend fun deleteEngine(gameId: String)

    @Query("SELECT * FROM GAME_STATE")
    suspend fun readStates(): List<GameStateEntity>

    @Query("SELECT * FROM PLAYER_DECISION WHERE game_state_id = :gameId ORDER BY position")
    suspend fun readDecisions(gameId: String): List<PlayerDecisionEntity>

    @Query("SELECT * FROM OWNED_ITEM WHERE game_state_id = :gameId ORDER BY position")
    suspend fun readOwnedItems(gameId: String): List<OwnedItemEntity>

    @Insert
    suspend fun insertState(state: GameStateEntity)

    @Update
    suspend fun updateState(state: GameStateEntity): Int

    @Insert
    suspend fun insertDecisions(rows: List<PlayerDecisionEntity>)

    @Insert
    suspend fun insertOwnedItems(rows: List<OwnedItemEntity>)

    @Query("DELETE FROM PLAYER_DECISION WHERE game_state_id = :gameId")
    suspend fun deleteDecisions(gameId: String)

    @Query("DELETE FROM OWNED_ITEM WHERE game_state_id = :gameId")
    suspend fun deleteOwnedItems(gameId: String)
}
