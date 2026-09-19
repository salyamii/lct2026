package ru.nksk.lctapp.domain.content

interface StoryContentRepository {
    suspend fun read(): StoryContent

    /**
     * Atomically installs new definitions. Identical rows are idempotent; existing IDs and their
     * effects/choices/requirements cannot be rewritten. Revisions need new definition IDs.
     * No authored story is fabricated by database initialization.
     */
    suspend fun install(content: StoryContent)
}
