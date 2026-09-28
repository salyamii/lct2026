package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.core.ui.game.eventMediaArtwork
import ru.nksk.lctapp.core.ui.media.BundledMediaCatalog
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
                media = EventMedia(artworkKey = "future.art", musicCueKey = "future.music", appearanceCueKey = "future.appearance",
                    ambientCueKey = "future.ambient", narrationCueKey = "future.voice",
                    actionAudio = mapOf("$id:buy" to EventActionAudio("future.sound", "future.answer")))))))
        assertEquals(originalFingerprint, GameCatalogFingerprint.compute(changed))
        assertNotEquals(originalFingerprint, GameCatalogFingerprint.compute(catalog.copy(
            rules = catalog.rules.copy(fullEnergy = catalog.rules.fullEnergy + 1))))
    }

    @Test fun authoredAudioResolvesAndFreeChoicesNeverGetThePaymentSound() {
        val choices = catalog.content.choices.associateBy { it.id }
        catalog.cards.forEach { (eventId, card) ->
            val media = card.presentation.media
            val cues = listOfNotNull(media.narrationCueKey, media.appearanceCueKey, media.ambientCueKey,
                media.musicCueKey) + media.actionAudio.values.flatMap { listOfNotNull(it.soundCueKey, it.voiceCueKey) }
            cues.forEach { assertNotNull("$eventId: $it", BundledMediaCatalog.assetPath(it)) }
            media.actionAudio.forEach { (choiceId, audio) ->
                val choice = checkNotNull(choices[choiceId]) { "No choice for audio: $choiceId" }
                assertEquals(eventId, choice.eventId)
                if (audio.soundCueKey == "sound.payment") assertTrue(choice.moneyDelta < 0)
            }
        }
        assertEquals("narration.story.marked_plate", catalog.cards.getValue(LORE_PLATE_CLEANING).presentation.media.narrationCueKey)
        assertEquals(setOf("$LORE_PLATE_CLEANING:pay"), catalog.cards.getValue(LORE_PLATE_CLEANING).presentation.media.actionAudio.keys)
        assertEquals(setOf("$PLATE_CLEANING:pay"), catalog.cards.getValue(PLATE_CLEANING).presentation.media.actionAudio.keys)
        assertNull(BundledMediaCatalog.assetPath("unknown.cue"))
    }

    @Test fun currentActProvidesOneConsistentChapterSoundtrack() {
        val acts = checkNotNull(catalog.storyCampaign).acts
        assertEquals(5, acts.size)
        acts.forEachIndexed { index, act ->
            val keys = act.eventIds.map { catalog.cards.getValue(it).presentation.media.musicCueKey }.toSet()
            assertEquals(setOf("story.chapter_${index + 1}"), keys)
        }
        assertEquals("story.chapter_1", catalog.cards.getValue(LORE_PLATE_CLEANING).presentation.media.musicCueKey)
    }

    @Test fun reportedPortDeedsKeepTheirLocationSoundIncludingRebalancedVersions() {
        for (title in listOf("Посылки по причалам", "Успеть до дождя", "Смотритель просит помочь")) {
            val versions = catalog.content.events.filter { it.title == title }
            assertTrue("Missing deed: $title", versions.isNotEmpty())
            versions.forEach { event ->
                assertEquals(event.id, "ambient.port", catalog.cards.getValue(event.id).presentation.media.ambientCueKey)
            }
        }
    }

    @Test fun telescopeAdjustmentUsesItsOwnShortEffectOnlyInTheTwoDeedVersions() {
        val expected = setOf("figma-2163-43-v1", "figma-2163-43-v1:balance-v2")
        val withEffect = catalog.cards.filterValues {
            it.presentation.media.appearanceCueKey == "sound.telescope_adjustment"
        }
        assertEquals(expected, withEffect.keys)
        withEffect.forEach { (id, card) ->
            assertNull(id, card.presentation.media.narrationCueKey)
            assertNull(id, card.presentation.media.ambientCueKey)
        }
        assertEquals("narration.story.telescope_journal",
            catalog.cards.getValue("campaign-choice-v1:G1.10").presentation.media.narrationCueKey)
    }
}
