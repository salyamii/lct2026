package ru.nksk.lctapp.domain.pet

/** Contract IDs describe series/color; age and fur only choose presentation artwork. */
data class ParentRewardCap(val itemId: String, val lookId: String, val title: String)

object ParentRewardCaps {
    // Reuse the catalog installed by withParentRewardAccessories; IDs and titles have one source.
    val all = PetCosmetics.parentRewards.map { cosmetic ->
        ParentRewardCap(cosmetic.itemIds.single(), cosmetic.lookId, cosmetic.title)
    }
    fun forItem(itemId: String): ParentRewardCap? = all.find { it.itemId == itemId }
}
