package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors upstream `ArtifactModes` (packages/data-provider/src/artifacts.ts).
 * Values are lowercase with no underscores -- typos like "shadcn_ui" or
 * "shadcnUI" silently break round-tripping.
 */
@Serializable
enum class ArtifactsMode {
    @SerialName("default")
    DEFAULT,

    @SerialName("shadcnui")
    SHADCN_UI,

    @SerialName("custom")
    CUSTOM,
    ;

    /** Wire-format value (the @SerialName). */
    val wire: String
        get() = when (this) {
            DEFAULT -> "default"
            SHADCN_UI -> "shadcnui"
            CUSTOM -> "custom"
        }

    companion object {
        fun fromWire(value: String?): ArtifactsMode? = when (value) {
            null -> null
            "default" -> DEFAULT
            "shadcnui" -> SHADCN_UI
            "custom" -> CUSTOM
            // Legacy mobile-only sentinel produced by older builds before the enum existed.
            // Treat as DEFAULT for back-compat.
            "artifacts" -> DEFAULT
            // Empty string means "off" upstream -- caller maps to null.
            "" -> null
            else -> null
        }
    }
}
