package com.garfiec.librechat.feature.settings.model

import androidx.compose.runtime.Immutable

@Immutable
data class SharedLinkDisplayData(
    val shareId: String,
    val title: String,
    val createdAt: String?,
    val isPublic: Boolean,
)
