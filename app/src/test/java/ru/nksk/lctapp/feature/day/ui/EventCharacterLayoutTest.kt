package ru.nksk.lctapp.feature.day.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nksk.lctapp.R
import ru.nksk.lctapp.core.ui.components.eventArtworkBounds
import ru.nksk.lctapp.core.ui.components.petArtworkGrounding

class EventCharacterLayoutTest {
    @Test fun adultWithBinocularsIsLargerWithoutOverlappingCaretaker() {
        val width = 336f
        val pet = R.drawable.ryzhik_adult_body_accessory_binoculars
        val npc = R.drawable.npc_caretaker_explaining
        val layout = layout(width, 252f, pet, npc)
        assertTrue(layout.pet.size > width * .56f)
        assertContainedAndSeparated(width, 252f, pet, npc, layout)
    }

    @Test fun widePosesAndCompactScenesKeepTheirCanvasAndVisibleFiguresInside() {
        val pets = listOf(R.drawable.ryzhik_senior_state_joy_sand,
            R.drawable.ryzhik_senior_state_joy_copper, R.drawable.ryzhik_adult_body_accessory_compass,
            R.drawable.ryzhik_adult_body_accessory_hat, R.drawable.ryzhik_senior_state_tired_dark_russet)
        val companions = listOf(R.drawable.npc_caretaker_explaining, R.drawable.npc_carpenter_explaining,
            R.drawable.npc_tiko_body, R.drawable.npc_luna_body, R.drawable.npc_butcher_tray)
        for (pet in pets) for (npc in companions) for (height in listOf(106f, 220f, 320f)) {
            assertContainedAndSeparated(336f, height, pet, npc, layout(336f, height, pet, npc))
        }
    }

    private fun layout(width: Float, height: Float, pet: Int, npc: Int) = eventCharacterLayout(
        width, height, .72f, requireNotNull(eventArtworkBounds(pet)),
        requireNotNull(eventArtworkBounds(npc)), petArtworkGrounding(pet))

    private fun assertContainedAndSeparated(width: Float, height: Float, pet: Int, npc: Int, layout: EventCharacterLayout) {
        val petBounds = requireNotNull(eventArtworkBounds(pet))
        val npcBounds = requireNotNull(eventArtworkBounds(npc))
        val contact = petArtworkGrounding(pet)
        val petShift = contact?.let { .935f - it.contactY } ?: 0f
        val petLeft = layout.pet.x + minOf(petBounds.left,
            contact?.let { it.centerX - it.shadowWidth / 2f } ?: petBounds.left) * layout.pet.size
        val petRight = layout.pet.x + maxOf(petBounds.right,
            contact?.let { it.centerX + it.shadowWidth / 2f } ?: petBounds.right) * layout.pet.size
        val petTop = layout.pet.y + (petBounds.top + petShift) * layout.pet.size
        val petBottom = layout.pet.y + maxOf(petBounds.bottom + petShift,
            contact?.let { .935f + it.shadowHeight / 2f } ?: .935f) * layout.pet.size
        val npcLeft = layout.companion.x + npcBounds.left * layout.companion.size
        val npcRight = layout.companion.x + npcBounds.right * layout.companion.size
        val npcTop = layout.companion.y + npcBounds.top * layout.companion.size
        val npcBottom = layout.companion.y + npcBounds.bottom * layout.companion.size
        assertTrue("NPC fits left", npcLeft >= 0f)
        assertTrue("Pet fits right", petRight <= width)
        assertTrue("Figures separated", petLeft > npcRight)
        assertTrue("Heads fit", minOf(petTop, npcTop) >= 0f)
        assertTrue("Feet and shadow fit", maxOf(petBottom, npcBottom) <= height)
        assertTrue(layout.pet.size > 0f && layout.companion.size > 0f)
    }
}
