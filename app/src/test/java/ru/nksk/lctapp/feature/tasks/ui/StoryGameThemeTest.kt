package ru.nksk.lctapp.feature.tasks.ui

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.data.game.content.bundledGameCatalog
import ru.nksk.lctapp.domain.engine.EventGameMedia
import ru.nksk.lctapp.domain.engine.EventMedia
import ru.nksk.lctapp.domain.minigame.DeedGameKind

class StoryGameThemeTest {
    @Test fun everyCurrentAndLegacyPracticalChoiceExplainsItsActualBoard() {
        val catalog = bundledGameCatalog()
        val assignments = catalog.policies.flatMap { (eventId, policy) ->
            policy.choiceGameKinds.map { (choiceId, kind) -> Triple(eventId, choiceId, kind) }
        }
        assertTrue(assignments.isNotEmpty())
        for ((eventId, choiceId, kind) in assignments) {
            val media = catalog.cards.getValue(eventId).presentation.media
            val theme = checkNotNull(storyGameTheme(media, kind)) { choiceId }
            val instructions = theme.instructions
            if (kind != DeedGameKind.MEMORY) {
                assertTrue(choiceId, instructions.startsWith(checkNotNull(media.game).context))
            }
            when (kind) {
                DeedGameKind.NEED_WANT, DeedGameKind.REAL_DISCOUNT, DeedGameKind.COIN_BALANCE,
                DeedGameKind.FINANCIAL_TRAP, DeedGameKind.EXTRA_PURCHASE -> fail("Financial games belong to paid deeds: $choiceId")
                DeedGameKind.MEMORY -> {
                    assertTrue(choiceId, instructions.contains("по две карточки"))
                    assertFalse(choiceId, instructions.contains("маркер"))
                }
                DeedGameKind.PRECISION -> {
                    assertTrue(choiceId, instructions.contains("маркер в зелёной зоне"))
                    assertFalse(choiceId, instructions.contains("по две карточки"))
                }
                DeedGameKind.COMPARISON -> {
                    assertTrue(choiceId, instructions.contains("следуй заданию раунда"))
                    assertFalse(choiceId, instructions.contains("маркер"))
                }
                DeedGameKind.LIGHTS -> assertTrue(choiceId, instructions.contains("фонарь и соседи"))
                DeedGameKind.SEQUENCE -> {
                    assertEquals(choiceId, checkNotNull(media.game).context, instructions)
                    assertTrue(choiceId, instructions.contains("порядок вспышек"))
                }
                DeedGameKind.PIPES -> assertTrue(choiceId, instructions.contains("концы одного цвета"))
                DeedGameKind.DIFFERENCES -> assertTrue(choiceId, instructions.contains("отличия между полками"))
                DeedGameKind.STACKING -> assertTrue(choiceId, instructions.contains("ящик на предыдущий"))
                DeedGameKind.TELESCOPE, DeedGameKind.MARK, DeedGameKind.WHEEL, DeedGameKind.STRAP,
                DeedGameKind.SIGNAL, DeedGameKind.ROUTE_MAP, DeedGameKind.BOAT,
                DeedGameKind.TRIP_PACKING, DeedGameKind.SHOPPING_COMPARISON,
                DeedGameKind.RECORD_JOURNAL, DeedGameKind.RECORD_LENSES, DeedGameKind.RECORD_LIGHTS,
                DeedGameKind.OBSERVATORY_OPENING, DeedGameKind.PLATE_HAND, DeedGameKind.PLATE_SOLUTION,
                DeedGameKind.LIGHT_INSCRIPTION, DeedGameKind.SERVICE_EMBLEM, DeedGameKind.SIGNAL_POST,
                DeedGameKind.CHAPTER_FINALE, DeedGameKind.RING_TOSS -> {
                    assertFalse(choiceId, instructions.contains("маркер в зелёной зоне"))
                    assertFalse(choiceId, instructions.contains("по две карточки"))
                }
            }
        }
    }

    @Test fun reusingAnActivityContextCannotCarryControlsFromADifferentBoard() {
        val media = EventMedia(game = EventGameMedia("Разберём находки."))
        val memory = checkNotNull(storyGameTheme(media, DeedGameKind.MEMORY)).instructions
        val precision = checkNotNull(storyGameTheme(media, DeedGameKind.PRECISION)).instructions
        val comparison = checkNotNull(storyGameTheme(media, DeedGameKind.COMPARISON)).instructions
        assertTrue(memory.contains("по две карточки"))
        assertFalse(memory.contains("маркер"))
        assertTrue(precision.contains("маркер в зелёной зоне"))
        assertFalse(precision.contains("по две карточки"))
        assertTrue(comparison.contains("следуй заданию раунда"))
        assertFalse(comparison.contains("маркер"))
    }

    @Test fun everyCurrentPaidDeedHasContextAndControlsForItsRealBoard() {
        val catalog = bundledGameCatalog()
        for (id in catalog.deedPool) {
            val kind = checkNotNull(catalog.policies.getValue(id).deedGameKind)
            val media = catalog.cards.getValue(id).presentation.media
            val theme = checkNotNull(storyGameTheme(media, kind)) { id }
            assertFalse(id, checkNotNull(media.game).context.isBlank())
            if (kind == DeedGameKind.SEQUENCE) {
                assertEquals(id, media.game?.context, theme.instructions)
            } else {
                assertTrue(id, theme.instructions.endsWith(storyGameInstructions(kind)))
            }
        }
        val relay = catalog.cards.getValue("campaign-choice-v2:G5.08").presentation.media
        assertTrue(checkNotNull(storyGameTheme(relay, DeedGameKind.LIGHTS)).instructions.contains("погасить все контрольные огни"))
    }
}
