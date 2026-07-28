package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.ConversationRepository

/**
 * Pulls the latest favorite-conversation markers from the server when a session starts
 * so the drawer's favorites section is correct before the user interacts with it.
 */
class SyncFavoritesSessionTask(
    private val conversationRepository: ConversationRepository,
) : SessionTask {
    override suspend fun run() {
        conversationRepository.syncFavoritesFromServer()
    }
}
