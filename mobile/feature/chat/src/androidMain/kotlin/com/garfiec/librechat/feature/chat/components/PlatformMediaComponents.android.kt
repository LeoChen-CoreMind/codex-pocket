package com.garfiec.librechat.feature.chat.components

actual fun shareArtifact(title: String, content: String, language: String) {
    // Artifact sharing is handled directly by ArtifactDownloadHelper.share() from the Android
    // ArtifactPanel implementation. This expect/actual is a no-op placeholder for cross-platform
    // callers. The actual Android sharing uses FileProvider which requires Context.
}
