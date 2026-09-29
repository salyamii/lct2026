package ru.nksk.lctapp.domain.backend

import ru.nksk.lctapp.domain.analytics.LedgerEntry
import ru.nksk.lctapp.domain.analytics.LedgerKind
import ru.nksk.lctapp.domain.economy.EconomyOperations
import ru.nksk.lctapp.domain.engine.DayJournalEntry
import ru.nksk.lctapp.domain.engine.DayJournalKind
import ru.nksk.lctapp.domain.game.GameState
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.domain.pet.PetCosmetics

enum class ParentCoinAllocation { RESERVE }

/** Unresolved policies defer their grants without acknowledgement; no UI or transport chooses a rule. */
data class ParentRewardPolicy(
    val coinAllocation: ParentCoinAllocation? = null,
    val duplicateAccessoryOutcome: ParentRewardOutcome? = null,
) {
    init { require(duplicateAccessoryOutcome == null || duplicateAccessoryOutcome == ParentRewardOutcome.ALREADY_OWNED) }

    fun apply(current: GameState, reward: ParentRewardDto, applicationId: String,
        installedAccessoryIds: Set<String>): ParentRewardChange? {
        val operationId = "$applicationId:income"
        val change = when (val payload = reward.reward) {
            is ParentRewardPayload.Coins -> {
                if (coinAllocation == null || current.economy.planning != null) return null
                val economy = when (coinAllocation) { ParentCoinAllocation.RESERVE -> EconomyOperations.earn(current.economy, payload.amount) }
                val periodId = current.financial.currentPeriodId
                val next = current.copy(economy = economy,
                    financial = current.financial.copy(periods = current.financial.periods.map { period ->
                        if (period.id == periodId) period.copy(income = Math.addExact(period.income, payload.amount)) else period
                    }),
                    engine = current.engine?.let { day -> day.copy(journal = day.journal +
                        DayJournalEntry(operationId, DayJournalKind.PARENT_REWARD, reward.rewardId, payload.amount)) },
                )
                ParentRewardChange(next, ParentRewardOutcome.APPLIED, listOf(LedgerEntry(operationId, LedgerKind.INCOME, payload.amount)))
            }
            is ParentRewardPayload.Accessory -> {
                if (payload.itemId !in installedAccessoryIds) return null
                val cosmetic = PetCosmetics.forItem(payload.itemId) ?: return null
                if (current.ownedItems.any { it.itemId in cosmetic.itemIds }) {
                    val outcome = if (ru.nksk.lctapp.domain.pet.ParentRewardCaps.forItem(payload.itemId) != null)
                        ParentRewardOutcome.ALREADY_OWNED else duplicateAccessoryOutcome ?: return null
                    ParentRewardChange(current, outcome, emptyList())
                } else ParentRewardChange(current.copy(ownedItems = current.ownedItems +
                    OwnedItem("$applicationId:item", payload.itemId),
                    engine = current.engine?.let { day -> day.copy(journal = day.journal +
                        DayJournalEntry("$applicationId:accessory", DayJournalKind.PARENT_REWARD, payload.itemId, 0)) },
                ), ParentRewardOutcome.APPLIED, emptyList())
            }
        }
        return if (change.state == current) change else change.copy(state = change.state.copy(
            engine = change.state.engine?.let { it.copy(revision = Math.addExact(it.revision, 1L)) },
        ))
    }
}

data class ParentRewardChange(val state: GameState, val outcome: ParentRewardOutcome, val operations: List<LedgerEntry>)
