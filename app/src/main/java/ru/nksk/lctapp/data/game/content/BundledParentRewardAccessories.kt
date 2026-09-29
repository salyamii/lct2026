package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.engine.GameCatalog
import ru.nksk.lctapp.domain.pet.PetCosmetics

/** Installing gift definitions enables delivery without granting, selling or equipping an item. */
internal fun GameCatalog.withParentRewardAccessories() = copy(content = content.copy(
    items = content.items + PetCosmetics.parentRewards.map { cosmetic ->
        ItemDefinition(cosmetic.itemIds.single(), cosmetic.title,
            "Подарок от родителя. Можно надеть в разделе «Снаряжение».", ItemCategory.ACCESSORY)
    },
))
