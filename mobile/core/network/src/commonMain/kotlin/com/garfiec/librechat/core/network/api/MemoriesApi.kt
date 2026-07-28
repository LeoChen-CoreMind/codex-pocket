package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

class MemoriesApi constructor(
    private val client: HttpClient,
) {

    suspend fun getMemories(): List<Memory> =
        client.get {
            url { path("api/memories") }
        }.body()

    suspend fun createMemory(request: CreateMemoryRequest): Memory =
        client.post {
            url { path("api/memories") }
            setBody(request)
        }.body()

    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): MemoryPreferences =
        client.patch {
            url { path("api/memories/preferences") }
            setBody(request)
        }.body()

    suspend fun updateMemory(key: String, request: UpdateMemoryRequest): Memory =
        client.patch {
            url { path("api/memories/${key.encodeURLPathPart()}") }
            setBody(request)
        }.body()

    suspend fun deleteMemory(key: String): Unit =
        client.delete {
            url { path("api/memories/${key.encodeURLPathPart()}") }
        }.body()
}
