package com.garfiec.librechat.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey
    val presetId: String,
    val title: String,
    val endpoint: String?,
    val model: String?,
    val isDefault: Boolean = false,
    val order: Int?,
    val params: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
