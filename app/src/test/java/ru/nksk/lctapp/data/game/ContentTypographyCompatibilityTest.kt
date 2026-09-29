package ru.nksk.lctapp.data.game

import org.junit.Assert.*
import org.junit.Test
import ru.nksk.lctapp.domain.content.*
import ru.nksk.lctapp.domain.economy.BudgetSection

class ContentTypographyCompatibilityTest {
    private fun original(): StoryContent = StoryContent(
        chapters = listOf(ChapterDefinition("chapter-\u2014", "Глава \u2014 первая", "goal-\u2014")),
        days = listOf(GameDayDefinition("day-\u2014", "chapter-\u2014", 1)),
        schedule = listOf(ScheduledEvent("schedule-\u2014", "day-\u2014", 0, "event-\u2014")),
        events = listOf(EventDefinition("event-\u2014", EventType.WANT, "Звёзды \u2014 рядом",
            "Сходить \u2014 и посмотреть", null, null, null, -3, BudgetSection.WANTS, null)),
        choices = listOf(EventChoiceDefinition("choice-\u2014", "event-\u2014", 0,
            "Пойдём \u2014 сейчас", 0, null, null, GoalImpact.NEUTRAL)),
        items = listOf(ItemDefinition("item-\u2014", "Книга \u2014 атлас", "Карта \u2014 внутри", priceCoins = 24)),
        goals = listOf(GoalDefinition("goal-\u2014", "Ночь \u2014 наблюдений", "Собрать \u2014 комплект")),
        requiredItems = listOf(GoalRequiredItem("goal-\u2014", "item-\u2014")),
        eventItemEffects = listOf(EventItemEffect("effect-\u2014", "event-\u2014", 0, "item-\u2014", ItemOperation.ADD)),
        choiceItemEffects = listOf(ChoiceItemEffect("choice-effect-\u2014", "choice-\u2014", 0, "item-\u2014", ItemOperation.REMOVE)),
    )

    @Test fun punctuationOnlyUpgradeKeepsExistingDefinitionsAndAllHistoricalIdentifiers() {
        val saved = original()
        val current = saved.withCurrentTypography()
        assertEquals(StoryContent(), current.newDefinitionsComparedTo(saved))
        assertEquals(StoryContent(), saved.newDefinitionsComparedTo(current))
        assertEquals("Звёзды - рядом", current.events.single().title)
        assertEquals("Сходить - и посмотреть", current.events.single().description)
        assertEquals("Пойдём - сейчас", current.choices.single().text)
        assertEquals("Книга - атлас", current.items.single().name)
        assertEquals("Карта - внутри", current.items.single().description)
        assertEquals("Глава - первая", current.chapters.single().title)
        assertEquals("Ночь - наблюдений", current.goals.single().title)
        assertEquals("Собрать - комплект", current.goals.single().description)
        assertEquals(saved.days, current.days)
        assertEquals(saved.schedule, current.schedule)
        assertEquals(saved.requiredItems, current.requiredItems)
        assertEquals(saved.eventItemEffects, current.eventItemEffects)
        assertEquals(saved.choiceItemEffects, current.choiceItemEffects)
        assertEquals("event-\u2014", current.events.single().id)
        assertEquals("event-\u2014", current.choices.single().eventId)
        assertEquals("goal-\u2014", current.chapters.single().goalId)
        assertEquals("Звёзды \u2014 рядом", saved.events.single().title)
        assertEquals(current, current.withCurrentTypography())
        assertEquals(current, saved.newDefinitionsComparedTo(StoryContent()))
    }

    @Test fun otherCopyChangesAndGameplayChangesAreStillRejected() {
        val saved = original()
        val current = saved.withCurrentTypography()
        listOf(
            current.copy(events = current.events.map { it.copy(description = "Совсем другой сюжет") }),
            current.copy(events = current.events.map { it.copy(description = "Сходить \u2013 и посмотреть") }),
            current.copy(events = current.events.map { it.copy(moneyDeltaOnStart = -2) }),
            current.copy(events = current.events.map { it.copy(maxFatigue = 4) }),
            current.copy(choices = current.choices.map { it.copy(moneyDelta = 1) }),
            current.copy(choices = current.choices.map { it.copy(position = 1) }),
            current.copy(items = current.items.map { it.copy(priceCoins = 25) }),
            current.copy(eventItemEffects = current.eventItemEffects.map { it.copy(operation = ItemOperation.REMOVE) }),
        ).forEach { changed ->
            assertTrue(runCatching { changed.newDefinitionsComparedTo(saved) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun punctuationCompatibilityDoesNotAllowAddingChildrenToExistingDefinitions() {
        val saved = original()
        val current = saved.withCurrentTypography()
        val addedChoice = current.choices.single().copy(id = "new-choice", position = 1)
        assertTrue(runCatching {
            current.copy(choices = current.choices + addedChoice).newDefinitionsComparedTo(saved)
        }.exceptionOrNull() is IllegalArgumentException)
    }
}
