package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.request.PresetDeleteRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.path

class PresetsApi constructor(
    private val client: HttpClient,
) {
    suspend fun getPresets(): List<Preset> =
        client.get {
            url { path("api/presets") }
        }.body()

    suspend fun createPreset(preset: Preset): Preset =
        client.post {
            url { path("api/presets") }
            setBody(preset)
        }.body()

    suspend fun updatePreset(preset: Preset): Preset =
        client.post {
            url { path("api/presets") }
            setBody(preset)
        }.body()

    suspend fun deletePreset(presetId: String) {
        client.post {
            url { path("api/presets/delete") }
            setBody(PresetDeleteRequest(presetId = presetId))
        }
    }
}
