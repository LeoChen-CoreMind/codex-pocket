package com.garfiec.librechat.core.data.datastore

enum class LatexRenderer {
    NATIVE, KATEX;

    companion object {
        fun fromString(value: String?): LatexRenderer = when (value) {
            "native" -> NATIVE
            else -> KATEX
        }
    }

    fun toStorageString(): String = when (this) {
        NATIVE -> "native"
        KATEX -> "katex"
    }
}
