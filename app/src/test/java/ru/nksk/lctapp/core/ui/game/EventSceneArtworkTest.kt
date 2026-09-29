package ru.nksk.lctapp.core.ui.game

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.data.game.content.bundledGameCatalog

class EventSceneArtworkTest {
    @Test fun newActivityVersionsKeepTheirNpcObjectAndLocation() {
        val catalog = bundledGameCatalog()
        val revisions = listOf("2238-120", "2270-54", "2270-106", "2289-2", "2289-110", "2289-164", "2289-218")
            .associate { "figma-$it-v1:balance-v2" to "figma-$it-v1:balance-v2:game-v3" } +
            listOf("G2.03", "G3.06", "G3.09", "G3.11", "G5.08")
                .associate { "campaign-choice-v1:$it" to "campaign-choice-v2:$it" } +
            listOf("2326-112", "2326-352").associate { "figma-$it-v2" to "figma-$it-v3" }
        for ((oldId, newId) in revisions) {
            val oldCard = catalog.cards.getValue(oldId)
            val newCard = catalog.cards.getValue(newId)
            val oldArt = eventSceneArtwork(oldId, oldCard.character)
            assertNotNull(oldId, oldArt)
            assertEquals(newId, oldArt, eventSceneArtwork(newId, newCard.character))
            assertEquals(newId, eventSceneBackground(oldId, oldCard.scene), eventSceneBackground(newId, newCard.scene))
        }
        assertEquals(R.drawable.location_butcher_shop, eventSceneBackground("figma-2289-164-v1:balance-v2:game-v3", "fair"))
        assertEquals(R.drawable.location_trail_day, eventSceneBackground("campaign-choice-v2:G5.08", ""))
    }
}
