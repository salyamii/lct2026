package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.pet.PetCosmetics

internal const val PARENT_CAP_DESCRIPTION = "Кепка"

/** Installing gift definitions enables delivery without granting, selling or equipping an item. */
internal fun GameCatalog.withParentRewardAccessories() = copy(content = content.copy(
    items = content.items + PetCosmetics.parentRewards.map { cosmetic ->
        ItemDefinition(cosmetic.itemIds.single(), cosmetic.title,
            PARENT_CAP_DESCRIPTION, ItemCategory.ACCESSORY)
    },
))
