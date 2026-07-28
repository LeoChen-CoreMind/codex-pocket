package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Preset

interface PresetRepository {
    suspend fun getAll(): Result<List<Preset>>
    suspend fun create(preset: Preset): Result<Preset>
    suspend fun update(preset: Preset): Result<Preset>
    suspend fun delete(presetId: String): Result<Unit>
}
