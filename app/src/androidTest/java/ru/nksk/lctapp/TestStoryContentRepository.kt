package ru.nksk.lctapp

import ru.nksk.lctapp.domain.content.StoryContent
import ru.nksk.lctapp.domain.content.StoryContentRepository

internal class TestStoryContentRepository : StoryContentRepository {
    private var value = StoryContent()
    override suspend fun read() = value
    override suspend fun install(content: StoryContent) { value = content }
}
