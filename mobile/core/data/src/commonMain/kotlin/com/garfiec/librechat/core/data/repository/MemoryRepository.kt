package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest

/** Repository for user memory CRUD operations. Uses memory key as unique identifier. */
interface MemoryRepository {
    suspend fun getMemories(): Result<List<Memory>>
    suspend fun createMemory(request: CreateMemoryRequest): Result<Memory>
    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences>
    suspend fun updateMemory(key: String, request: UpdateMemoryRequest): Result<Memory>
    suspend fun deleteMemory(key: String): Result<Unit>
}
