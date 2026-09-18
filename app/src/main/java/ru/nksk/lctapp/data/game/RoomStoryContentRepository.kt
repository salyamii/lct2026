package ru.nksk.lctapp.data.game

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import javax.inject.Inject
import ru.nksk.lctapp.data.game.local.GameDatabase
import ru.nksk.lctapp.data.game.local.toDomain
import ru.nksk.lctapp.data.game.local.toEntity
import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository

internal class RoomStoryContentRepository @Inject constructor(private val database: GameDatabase) : StoryContentRepository {
    private val dao = database.storyContentDao()

    override suspend fun read(): StoryContent = database.withReadTransaction { readInTransaction() }

    override suspend fun install(content: StoryContent) {
        database.withWriteTransaction {
            val existing = readInTransaction()
            val added = content.newDefinitionsComparedTo(existing)
            validateStoryItemRewards(existing, added)
            // Referenced parents first. Every insert aborts on constraint violations.
            dao.insertGoal(added.goals.map { it.toEntity() })
            dao.insertItem(added.items.map { it.toEntity() })
            dao.insertChapter(added.chapters.map { it.toEntity() })
            dao.insertGameDay(added.days.map { it.toEntity() })
            dao.insertEvent(added.events.map { it.toEntity() })
            dao.insertDayEvent(added.schedule.map { it.toEntity() })
            dao.insertEventChoice(added.choices.map { it.toEntity() })
            dao.insertGoalRequiredItem(added.requiredItems.map { it.toEntity() })
            dao.insertEventItemEffect(added.eventItemEffects.map { it.toEntity() })
            dao.insertChoiceItemEffect(added.choiceItemEffects.map { it.toEntity() })
        }
    }

    private suspend fun readInTransaction() = StoryContent(
        chapters = dao.readChapter().map { it.toDomain() },
        days = dao.readGameDay().map { it.toDomain() },
        schedule = dao.readDayEvent().map { it.toDomain() },
        events = dao.readEvent().map { it.toDomain() },
        choices = dao.readEventChoice().map { it.toDomain() },
        items = dao.readItem().map { it.toDomain() },
        goals = dao.readGoal().map { it.toDomain() },
        requiredItems = dao.readGoalRequiredItem().map { it.toDomain() },
        eventItemEffects = dao.readEventItemEffect().map { it.toDomain() },
        choiceItemEffects = dao.readChoiceItemEffect().map { it.toDomain() },
    )
}
