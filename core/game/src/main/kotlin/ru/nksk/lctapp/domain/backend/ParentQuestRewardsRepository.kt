package ru.nksk.lctapp.domain.backend

import kotlinx.coroutines.flow.Flow

data class ParentRewardInventory(val gameRunId: String, val ownedItemIds: Set<String>)
data class PendingParentQuestReward(val gameRunId: String, val itemId: String)

interface ParentQuestRewardsRepository {
    fun inventory(): Flow<ParentRewardInventory?>
    suspend fun pending(questId: String): PendingParentQuestReward?
    /** Completes only after the server grant is committed to the current local inventory. */
    suspend fun issue(questId: String, itemId: String, expectedRunId: String)
}

class ParentQuestRewardException(message: String) : IllegalStateException(message)
