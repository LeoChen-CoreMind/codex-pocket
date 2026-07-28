package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FeedbackRating {
    @SerialName("thumbsUp")
    THUMBS_UP,

    @SerialName("thumbsDown")
    THUMBS_DOWN,
}
