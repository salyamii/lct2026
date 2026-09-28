package ru.nksk.lctapp.feature.tasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.minigame.MemoryState

class MemoryPairNamesTest {
    @Test fun everyBundledPairSetHasDistinctLocalizedNamesForItsDifferentPictures() {
        val authoredSets = bundledGameCatalog().cards.values.mapNotNull { card ->
            card.presentation.media.game?.pairArtworkKeys?.takeIf { it.isNotEmpty() }?.map { key ->
                checkNotNull(eventMediaArtwork(key)) { key }
            }
        }
        for (artwork in (listOf(defaultMemoryPairArtwork) + authoredSets).distinct()) {
            assertEquals(MemoryState.PAIRS, artwork.size)
            val names = artwork.map { resource ->
                memoryPairNameResource(resource).also { assertNotNull("Unnamed pair artwork: $resource", it) }
            }
            assertEquals("Different pictures must remain distinguishable without sight", artwork.distinct().size, names.distinct().size)
        }
    }
}
