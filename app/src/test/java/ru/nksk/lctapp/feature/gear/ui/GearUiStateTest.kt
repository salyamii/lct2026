package ru.nksk.lctapp.feature.gear.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.game.OwnedItem
import ru.nksk.lctapp.R

class GearUiStateTest {
    private val catalog = listOf(
        ItemDefinition("map", "Карта", "Старинная карта", priceCoins = 50),
        ItemDefinition("hat", "Шляпа", "Шляпа с пером", ItemCategory.ACCESSORY, 80),
        ItemDefinition("compass", "Компас", "Латунный компас", priceCoins = 80),
    )

    @Test fun onlyOwnedOccurrencesAppearAndOrderAndDuplicatesArePreserved() {
        val state = gearUiState(
            listOf(OwnedItem("hat-1", "hat"), OwnedItem("map-2", "map"), OwnedItem("map-1", "map")),
            catalog, "Тоша",
        )
        assertEquals(listOf("map-2", "map-1"), state.storyItems.map { it.occurrenceId })
        assertEquals(listOf("hat-1"), state.accessories.map { it.occurrenceId })
        assertEquals("Карта", state.storyItems.first().name)
        assertEquals(50L, state.storyItems.first().priceCoins)
    }

    @Test fun emptyOwnershipDoesNotShowCatalogOrSelectedLook() {
        val state = gearUiState(emptyList(), catalog, "Тоша")
        assertTrue(state.storyItems.isEmpty())
        assertTrue(state.accessories.isEmpty())
        assertNull(state.ownedOccurrence("map"))
        assertNull(state.ownedOccurrence(null))
    }

    @Test fun everyPurchasedGoalPartHasItsOwnIllustrationAndReopenablePage() {
        val game = bundledGameCatalog()
        val parts = game.goals.flatMap { it.itemIds }
        assertEquals(23, parts.size)
        val owned = parts.mapIndexed { index, itemId -> OwnedItem("purchase-$index", itemId) }
        val state = gearUiState(owned, game.content.items, "Тоша")
        assertEquals(parts, state.storyItems.map { it.itemId })
        assertEquals(23, state.storyItems.map { it.artworkRes }.distinct().size)
        state.storyItems.forEach { item ->
            assertNotNull(item.name, item.artworkRes)
            assertTrue(item.name, item.pages.isNotEmpty())
            assertTrue(item.name, item.pages.all { it.imageRes != null })
            assertEquals(item, state.ownedOccurrence(item.occurrenceId))
            assertNull(state.ownedOccurrence(item.itemId))
        }
        // Browsing and reconstructing presentation use the same persisted occurrences.
        assertEquals(state, gearUiState(owned, game.content.items, "Тоша"))
        assertTrue(gearUiState(emptyList(), game.content.items, "Тоша").storyItems.isEmpty())
    }

    @Test fun starMapPagesBelongToEachOwnedOccurrenceAndDisappearWhenOwnershipDoes() {
        val item = ItemDefinition("stargazing-star-map-v1", "Карта звёзд", "Карта для наблюдений")
        val owned = listOf(OwnedItem("map-first", item.id), OwnedItem("map-second", item.id))
        val state = gearUiState(owned, listOf(item), "Тоша")
        val first = state.ownedOccurrence("map-first")!!
        assertEquals(listOf("orion-belt", "big-dipper", "milky-way"), first.pages.map { it.id })
        assertTrue(first.pages.all { it.zoomable })
        assertTrue(first.pages.all { it.body.isNotBlank() })
        assertEquals(first.pages, state.ownedOccurrence("map-second")!!.pages)
        assertEquals(listOf("map-first", "map-second"), state.storyItems.map { it.occurrenceId })
        val removed = gearUiState(owned.drop(1), listOf(item), "Тоша")
        assertNull(removed.ownedOccurrence("map-first"))
        assertNotNull(removed.ownedOccurrence("map-second"))
    }

    @Test fun unknownItemKeepsItsRealDescriptionWithoutInventedArtworkOrPages() {
        val item = ItemDefinition("new-find", "Находка {petName}", "{petName} нашёл деревянную метку")
        val state = gearUiState(listOf(OwnedItem("find", item.id)), listOf(item), "Тоша")
        val shown = state.ownedOccurrence("find")!!
        assertNull(shown.artworkRes)
        assertEquals("Находка Тоша", shown.pages.single().title)
        assertEquals("Тоша нашёл деревянную метку", shown.pages.single().body)
        assertFalse(shown.pages.single().zoomable)
        assertEquals("{petName} нашёл деревянную метку", item.description)
    }

    @Test fun itemTemplatesUseTheCurrentPetNameWithoutChangingTheCatalog() {
        val item = ItemDefinition("map", "Карта", "{petName} отмечает созвездия")
        val state = gearUiState(listOf(OwnedItem("owned", item.id)), listOf(item), "Тоша")
        assertEquals("Тоша отмечает созвездия", state.storyItems.single().description)
        assertEquals("{petName} отмечает созвездия", item.description)
    }

    @Test fun arbitraryAccessoryIdsUseTheirExplicitCategory() {
        val item = ItemDefinition("new-cosmetic", "Брошь", "Звезда", ItemCategory.ACCESSORY)
        val state = gearUiState(listOf(OwnedItem("owned", item.id)), listOf(item), "Тоша")
        assertEquals("Брошь", state.accessories.single().name)
        assertNull(state.accessories.single().priceCoins)
        assertNull(state.accessories.single().lookId)
    }

    @Test fun legacyBoughtCapCanBeEquippedWithoutChangingItsInstalledDefinition() {
        val legacy = ItemDefinition("figma-2164-2-explorer-cap-v1", "Кепка", "Старая версия", priceCoins = 25)
        val state = gearUiState(listOf(OwnedItem("old-purchase", legacy.id)), listOf(legacy), "Лис", "HAT")
        assertTrue(state.storyItems.isEmpty())
        assertEquals("HAT", state.accessories.single().lookId)
        assertTrue(state.accessories.single().equipped)
        assertEquals(ItemCategory.STORY, legacy.category)
        assertEquals(25L, legacy.priceCoins)
    }

    @Test fun starterAccessoriesHaveTheirOriginalArtAndStayVisibleAfterRemoval() {
        val definitions = bundledGameCatalog().content.items
        for ((id, look, art) in listOf(
            Triple("starter-bandana-v1", "BANDANA", R.drawable.gear_blue_bandana),
            Triple("starter-backpack-v1", "BACKPACK", R.drawable.gear_explorer_backpack),
        )) {
            val ownership = listOf(OwnedItem("starter", id))
            val worn = gearUiState(ownership, definitions, "Лис", look).accessories.single()
            assertEquals(look, worn.lookId)
            assertEquals(art, worn.artworkRes)
            assertTrue(worn.equipped)
            assertTrue(worn.isStarterAccessory)
            assertEquals(worn.copy(equipped = false), gearUiState(ownership, definitions, "Лис", "PLAIN").accessories.single())
            assertTrue(gearUiState(emptyList(), definitions, "Лис", look).accessories.isEmpty())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun missingDefinitionIsAnErrorInsteadOfSilentlyDroppingOwnedItem() {
        gearUiState(listOf(OwnedItem("owned", "missing")), catalog, "Тоша")
    }
}
