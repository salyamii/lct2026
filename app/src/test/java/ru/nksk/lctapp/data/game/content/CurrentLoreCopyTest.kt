package ru.nksk.lctapp.data.game.content

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.engine.EventCardVariant
import ru.nksk.lctapp.domain.engine.EventPresentation
import ru.nksk.lctapp.domain.engine.StoryCondition

class CurrentLoreCopyTest {
    @Test fun currentCopyDoesNotReplaceImmutableStoryDefinitions() {
        val catalog = bundledGameCatalog()
        val id = storyEventId("G2.04")
        val source = sourceLoreCards.single { it.id == "G2.04" }
        assertEquals(source.body, catalog.content.events.single { it.id == id }.description)
        assertTrue(catalog.cards.getValue(id).presentation.body!!.contains("автоматический ответ реле"))
    }

    @Test fun authoredCopyAndConditionalVariantsKeepPrecedence() {
        val source = LoreScene("G2.04")
        val own = EventPresentation(title = "Свой заголовок", body = "Свои условия")
        assertEquals(own, own.withCurrentLoreCopy(source))
        assertNull(EventPresentation().withCurrentLoreCopy(source.copy(body = "Условия выбора")).body)
        assertNull(EventPresentation().withCurrentLoreCopy(source.copy(variants = listOf(
            EventCardVariant(StoryCondition.Always, "Текст для текущего состояния"),
        ))).body)
        assertNull(EventPresentation().withCurrentLoreCopy(LoreScene("G3.05", title = "Авторский заголовок")).title)
    }
}
