package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Inline artifact previews are capped to this fraction of the current window
 * height. The full artifact remains available via the fullscreen [ArtifactPanel]
 * on tap; the inline view is a "card preview" of the top portion. The cap also
 * keeps the Android software-layer WebView's backing bitmap inside the
 * paintable range — see `InlineArtifactView.android.kt` for the rendering
 * rationale.
 */
internal const val INLINE_MAX_HEIGHT_FRACTION = 0.7f
