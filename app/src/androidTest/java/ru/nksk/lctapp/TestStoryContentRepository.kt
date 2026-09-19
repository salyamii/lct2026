package ru.nksk.lctapp

import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository

internal class TestStoryContentRepository : StoryContentRepository {
    override suspend fun read() = StoryContent()
    override suspend fun install(content: StoryContent) = error("Navigation does not install content")
}
