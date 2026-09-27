package ru.nksk.lctapp.domain.backend

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** An immutable server grant, never a replacement balance or world snapshot. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ParentRewardPayload {
    @Serializable
    @SerialName("COINS")
    data class Coins(val amount: Long) : ParentRewardPayload {
        init { require(amount > 0) }
    }

    @Serializable
    @SerialName("ACCESSORY")
    data class Accessory(val itemId: String) : ParentRewardPayload {
        init { require(itemId.isNotBlank()) }
    }
}

/** Sent by an authenticated linked parent; the child transport cannot issue rewards. */
@Serializable
data class CreateParentRewardRequest(
    val gameRunId: String,
    val reward: ParentRewardPayload,
    val schemaVersion: Int = 1,
)

@Serializable
data class ParentRewardDto(
    val rewardId: String,
    val profileId: String,
    val gameRunId: String,
    val sequence: Long,
    val reward: ParentRewardPayload,
    val createdAt: String,
) {
    init { require(sequence > 0) }
}

/** Includes acknowledged grants. A cursor outside the restored world is not proof of application. */
@Serializable
data class ParentRewardsResponse(
    val profileId: String,
    val gameRunId: String,
    val rewards: List<ParentRewardDto>,
    val nextAfterSequence: Long,
    val hasMore: Boolean,
    val schemaVersion: Int = 1,
) {
    init { require(nextAfterSequence >= 0) }
}

@Serializable
enum class ParentRewardOutcome { APPLIED, ALREADY_OWNED }

/** Created with the local aggregate commit, sent only after it succeeds. */
@Serializable
data class ParentRewardReceiptDto(
    val rewardId: String,
    val applicationId: String,
    val historyEntryId: String,
    val historySequence: Long,
    val outcome: ParentRewardOutcome,
) {
    init { require(historySequence > 0) }
}

@Serializable
data class AckParentRewardsRequest(
    val gameRunId: String,
    val receipts: List<ParentRewardReceiptDto>,
    val schemaVersion: Int = 1,
) {
    init { require(receipts.isNotEmpty() && receipts.size <= 100) }
}

/** Delivery telemetry only: acknowledgement never removes a grant from the server ledger. */
@Serializable
data class AckParentRewardsResponse(
    val gameRunId: String,
    val acceptedApplicationIds: List<String>,
    val schemaVersion: Int = 1,
)
