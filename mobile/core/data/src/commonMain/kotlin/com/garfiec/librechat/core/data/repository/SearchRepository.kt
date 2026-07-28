package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Conversation

interface SearchRepository {
    suspend fun search(query: String): Result<List<Conversation>>
}
