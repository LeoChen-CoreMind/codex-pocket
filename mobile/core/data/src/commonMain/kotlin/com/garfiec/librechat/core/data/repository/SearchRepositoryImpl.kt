package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.network.api.ConversationsApi

class SearchRepositoryImpl(
    private val conversationsApi: ConversationsApi,
) : SearchRepository {

    override suspend fun search(query: String): Result<List<Conversation>> =
        safeApiCall {
            val response = conversationsApi.getConversations(search = query)
            response.conversations
        }
}
