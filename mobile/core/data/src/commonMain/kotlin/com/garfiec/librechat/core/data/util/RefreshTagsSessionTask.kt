package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.TagRepository

/**
 * Refreshes conversation tags from the server when a session starts so the drawer has
 * up-to-date tag counts. Fires in parallel with other session tasks.
 */
class RefreshTagsSessionTask(
    private val tagRepository: TagRepository,
) : SessionTask {
    override suspend fun run() {
        tagRepository.refreshTags()
    }
}
