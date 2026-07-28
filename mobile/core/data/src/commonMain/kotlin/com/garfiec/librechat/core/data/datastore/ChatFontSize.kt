package com.garfiec.librechat.core.data.datastore

enum class ChatFontSize {
    SMALL, MEDIUM, LARGE;

    companion object {
        fun fromString(value: String?): ChatFontSize = when (value) {
            "small" -> SMALL
            "large" -> LARGE
            else -> MEDIUM
        }
    }

    fun toStorageString(): String = when (this) {
        SMALL -> "small"
        MEDIUM -> "medium"
        LARGE -> "large"
    }
}
