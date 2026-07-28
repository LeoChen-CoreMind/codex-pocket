package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.core.model.response.KeyExpiryResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

class KeysApi(
    private val client: HttpClient,
) {
    suspend fun getKeyExpiry(name: String): KeyExpiryResponse =
        client.get {
            url { path("api/keys") }
            parameter("name", name)
        }.body()

    suspend fun updateKey(request: UpdateKeyRequest): Unit =
        client.put {
            url { path("api/keys") }
            setBody(request)
        }.body()

    suspend fun deleteKey(name: String): Unit =
        client.delete {
            url { path("api/keys/${name.encodeURLPathPart()}") }
        }.body()

    suspend fun deleteAllKeys(): Unit =
        client.delete {
            url { path("api/keys") }
            parameter("all", true)
        }.body()
}
