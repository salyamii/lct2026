package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.Serializable

/** The immutable grant and its durable application receipt travel together in world backups. */
@Serializable
data class ParentRewardApplication(
    val reward: ParentRewardDto,
    val receipt: ParentRewardReceiptDto,
) {
    init { require(reward.rewardId == receipt.rewardId) }
}

/** An in-flight server response belongs to the world that was replaced or reset. */
class ParentRewardTargetChangedException : IllegalStateException("Parent rewards target a different local world")

/** Reusing a grant identity with different immutable data never changes the original application. */
class ParentRewardConflictException(val rewardId: String) : IllegalArgumentException("Conflicting parent reward: $rewardId")
