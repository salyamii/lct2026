package ru.nksk.lctapp.data.game

import androidx.room3.withWriteTransaction
import javax.inject.Inject
import ru.nksk.lctapp.data.game.local.GameDatabase

/** Explicit debug-only deletion of the whole save, never a storage error fallback. */
internal class DebugGameResetRepository @Inject constructor(private val database: GameDatabase) {
    suspend fun reset() {
        database.withWriteTransaction {
            // Children first: the schema intentionally has no cascading deletes.
            for (table in listOf(
                "BUDGET_PLANNING", "DAY_JOURNAL", "ENGINE_EVENT", "ENGINE_DEED", "ENGINE_STATE", "MINI_GAME_COMPLETION",
                "GOAL_SELECTION", "COMPLETED_GOAL_PROJECT",
                "PLAYER_DECISION", "OWNED_ITEM", "LEGACY_EXPENSE_STATE", "GAME_STATE", "ONBOARDING_DRAFT",
            )) {
                usePrepared("DELETE FROM $table") { it.step() }
            }
        }
    }
}
