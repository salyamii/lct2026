package ru.nksk.lctapp.data.game.content

import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.engine.GameCatalog

/** New immutable definitions; installing the catalog does not grant either accessory. */
internal fun GameCatalog.withStarterAccessories() = copy(content = content.copy(items = content.items + listOf(
    ItemDefinition("starter-bandana-v1", "Бандана", "Бандана, с которой началось наше приключение.", ItemCategory.ACCESSORY),
    ItemDefinition("starter-backpack-v1", "Рюкзак", "Рюкзак, с которым началось наше приключение.", ItemCategory.ACCESSORY),
)))
