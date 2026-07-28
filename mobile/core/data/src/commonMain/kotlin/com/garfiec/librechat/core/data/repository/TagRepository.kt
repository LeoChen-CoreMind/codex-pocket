package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.ConversationTag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeTags(): Flow<List<ConversationTag>>
    suspend fun refreshTags(): Result<Unit>
    suspend fun setConversationTags(conversationId: String, tags: List<String>): Result<Unit>
    suspend fun toggleFavorite(conversationId: String, currentTags: List<String>): Result<Unit>
    suspend fun clearCache()
}
