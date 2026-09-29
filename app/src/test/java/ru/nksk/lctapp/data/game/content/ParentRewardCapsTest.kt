package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.cosmeticArtwork
import ru.nksk.lctapp.core.ui.game.rewardCapArtwork
import ru.nksk.lctapp.core.ui.game.toAdventurePetPresentation
import ru.nksk.lctapp.data.game.newDefinitionsComparedTo
import ru.nksk.lctapp.domain.content.ItemCategory
import ru.nksk.lctapp.domain.pet.*

class ParentRewardCapsTest {
    @Test fun catalogAddsSixNamedGiftsWithoutChangingExistingItemsOrCreatingPurchaseEffects() {
        val content = bundledGameCatalog().content
        val ids = PetCosmetics.parentRewards.flatMap { it.itemIds }.toSet()
        assertEquals(6, ids.size)
        val previous = content.copy(items = content.items.filterNot { it.id in ids })
        val added = content.newDefinitionsComparedTo(previous)
        assertEquals(ids, added.items.map { it.id }.toSet())
        assertTrue(added.events.isEmpty())
        assertTrue(added.choices.isEmpty())
        for (cosmetic in PetCosmetics.parentRewards) {
            val item = added.items.single { it.id == cosmetic.itemIds.single() }
            assertEquals(cosmetic.title, item.name)
            assertEquals(ItemCategory.ACCESSORY, item.category)
            assertNull(item.priceCoins)
            assertFalse(content.requiredItems.any { it.itemId == item.id })
            assertFalse(content.eventItemEffects.any { it.itemId == item.id })
            assertFalse(content.choiceItemEffects.any { it.itemId == item.id })
        }
    }

    @Test fun eachGiftHasAnIconAndAllTwelveFoxVariantsReturnAfterEveryReaction() {
        val bodies = mutableSetOf<Int>()
        val icons = mutableSetOf<Int>()
        for (cosmetic in PetCosmetics.parentRewards) {
            val icon = checkNotNull(cosmeticArtwork(cosmetic.lookId))
            assertTrue("Each named cap needs its own gear illustration", icons.add(icon))
            for (age in PetAge.entries) for (color in PetColor.entries) {
                val body = checkNotNull(rewardCapArtwork(cosmetic.lookId, age, color))
                assertTrue("Age, fur and cap variants must not silently share another sprite", bodies.add(body))
                for (reaction in PetVisualState.entries) {
                    val pet = PetState(cosmetic.lookId, reaction, age = age, color = color)
                    assertEquals(body, pet.toAdventurePetPresentation(showReaction = false).artworkRes)
                    assertEquals(cosmetic.lookId, pet.selectedLookId)
                    assertEquals(reaction, pet.visualState)
                }
            }
        }
        assertEquals(72, bodies.size)
        assertEquals(6, icons.size)
        assertNull(rewardCapArtwork("HAT", PetAge.CUB, PetColor.COPPER))
        assertNull(rewardCapArtwork("CAP_UNKNOWN", PetAge.CUB, PetColor.COPPER))
    }
}
