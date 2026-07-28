package com.garfiec.librechat.feature.chat.components.artifact

import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs

/**
 * Maps an artifact's MIME type to its [InlineArtifactPrefs] field via
 * [ArtifactType]. Plain text and unrecognised code always render as the
 * tap-to-open button (no toggle).
 */
fun InlineArtifactPrefs.shouldRenderInline(type: String): Boolean =
    when (ArtifactType.from(type)) {
        ArtifactType.MERMAID -> mermaid
        ArtifactType.SVG -> svg
        ArtifactType.HTML -> html
        ArtifactType.REACT -> react
        ArtifactType.MARKDOWN -> markdown
        ArtifactType.PLAIN, ArtifactType.CODE -> false
    }
