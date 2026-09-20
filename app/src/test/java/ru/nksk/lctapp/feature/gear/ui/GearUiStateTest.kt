package ru.nksk.lctapp.feature.gear.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.content.ItemDefinition
import ru.nksk.lctapp.domain.game.OwnedItem

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
    }

    @Test(expected = IllegalStateException::class)
    fun missingDefinitionIsAnErrorInsteadOfSilentlyDroppingOwnedItem() {
        gearUiState(listOf(OwnedItem("owned", "missing")), catalog, "Тоша")
    }
}
