package com.garfiec.librechat.core.data.util

import com.garfiec.librechat.core.data.repository.ConfigRepository

/**
 * Eagerly fetches `/api/config` endpoint configs when a session starts so the drawer's
 * first frame after cold-start (or fresh login) can resolve `EndpointConfig.iconURL`
 * for custom endpoints. Without this task, the drawer races against the chat screen's
 * lazy fetch — empty configs leak through as missing icons until the user taps a chat.
 */
class EndpointConfigFetchSessionTask(
    private val configRepository: ConfigRepository,
) : SessionTask {
    override suspend fun run() {
        configRepository.fetchEndpoints()
    }
}
