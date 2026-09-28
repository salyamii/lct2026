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
        assertEquals(28, assignments.size)
        for ((eventId, choiceId, kind) in assignments) {
            val media = catalog.cards.getValue(eventId).presentation.media
            val theme = checkNotNull(storyGameTheme(media, kind)) { choiceId }
            val instructions = theme.instructions
            assertTrue(choiceId, instructions.startsWith(checkNotNull(media.game).context))
            when (kind) {
                DeedGameKind.MEMORY -> {
                    assertTrue(choiceId, instructions.contains("по две карточки"))
                    assertFalse(choiceId, instructions.contains("маркер"))
                }
                DeedGameKind.PRECISION -> {
                    assertTrue(choiceId, instructions.contains("маркер в зелёной зоне"))
                    assertFalse(choiceId, instructions.contains("по две карточки"))
                }
                DeedGameKind.COMPARISON -> {
                    assertTrue(choiceId, instructions.contains("выбери большее"))
                    assertFalse(choiceId, instructions.contains("маркер"))
                }
                DeedGameKind.LIGHTS -> assertTrue(choiceId, instructions.contains("фонарь и соседи"))
                DeedGameKind.SEQUENCE -> assertTrue(choiceId, instructions.contains("вспышки башни"))
                DeedGameKind.PIPES -> assertTrue(choiceId, instructions.contains("концы одного цвета"))
                DeedGameKind.DIFFERENCES -> assertTrue(choiceId, instructions.contains("отличия между полками"))
                DeedGameKind.STACKING -> assertTrue(choiceId, instructions.contains("ящик на предыдущий"))
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
        assertTrue(comparison.contains("выбери большее"))
        assertFalse(comparison.contains("маркер"))
    }
}
