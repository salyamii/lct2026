package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.domain.engine.EventActionAudio
import ru.nksk.lctapp.domain.engine.EventLayout
import ru.nksk.lctapp.domain.engine.EventMedia
import ru.nksk.lctapp.domain.engine.EventPresentation
import ru.nksk.lctapp.domain.engine.displayAction
import ru.nksk.lctapp.domain.engine.displayOutcome
import ru.nksk.lctapp.domain.engine.displayTitle
import ru.nksk.lctapp.domain.timemachine.GameCatalogFingerprint

class EventPresentationTest {
    private val catalog = bundledGameCatalog()

    @Test fun currentNamesDoNotRewriteHistoricalEventAndChoiceDefinitions() {
        val event = catalog.content.events.single { it.id == "figma-2654-98-purchase-v2" }
        val buy = catalog.content.choices.single { it.id == "${event.id}:buy" }
        val pass = catalog.content.choices.single { it.id == "${event.id}:pass" }
        assertEquals("Ярмарочная игра за 7", event.title)
        assertEquals("Купить · 7", buy.text)
        assertEquals(-7L, buy.moneyDelta)
        assertEquals("Кольцеброс", catalog.displayTitle(event))
        assertEquals("Сыграть", catalog.displayAction(buy))
        assertEquals("Отказались от покупки: Кольцеброс", catalog.displayOutcome(pass))
    }

    @Test fun authoredIllustrationsAndMiniGamePairsResolveWithoutChangingOrder() {
        catalog.cards.forEach { (id, card) ->
            val media = card.presentation.media
            val keys = listOfNotNull(media.artworkKey) + media.game?.pairArtworkKeys.orEmpty() +
                listOfNotNull(media.game?.objectArtworkKey)
            keys.forEach { key -> assertNotNull("$id: $key", eventMediaArtwork(key)) }
        }
        val pairs = StoryGamePresentation.SORT_OLD_SHELF.media.pairArtworkKeys
        assertEquals(8, pairs.size)
        assertEquals("deed.goods_lens", pairs.first())
        assertEquals("story.chronoscope_ring", pairs.last())
        assertNull(eventMediaArtwork("unavailable.illustration"))
    }

    @Test fun mediaAndCurrentCopyDoNotInvalidateReplayButGameplayStillDoes() {
        val originalFingerprint = GameCatalogFingerprint.compute(catalog)
        val id = "figma-2654-98-purchase-v2"
        val card = catalog.cards.getValue(id)
        val changed = catalog.copy(cards = catalog.cards + (id to card.copy(
            presentation = EventPresentation(layout = EventLayout.PURCHASE, title = "Новая подпись",
                body = "Новый текст", locationTitle = "Другое название места",
                actionLabels = mapOf("$id:buy" to "Другой глагол"),
                media = EventMedia(artworkKey = "future.art", musicCueKey = "future.music",
                    ambientCueKey = "future.ambient", narrationCueKey = "future.voice",
                    actionAudio = mapOf("$id:buy" to EventActionAudio("future.sound", "future.answer")))))))
        assertEquals(originalFingerprint, GameCatalogFingerprint.compute(changed))
        assertNotEquals(originalFingerprint, GameCatalogFingerprint.compute(catalog.copy(
            rules = catalog.rules.copy(fullEnergy = catalog.rules.fullEnergy + 1))))
    }
}
