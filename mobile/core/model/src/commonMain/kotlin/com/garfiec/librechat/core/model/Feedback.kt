package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Feedback(
    val rating: FeedbackRating,
    val tag: JsonElement? = null,
    val text: String? = null,
)
