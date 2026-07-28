package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Immutable

@Immutable
data class AttachedFile(
    val uri: Any,
    val name: String,
    val isImage: Boolean = false,
    val uploadProgress: Float? = null,
    /** Server-assigned file ID after successful upload. Null while uploading. */
    val fileId: String? = null,
    /** Server file path returned from upload. */
    val filepath: String? = null,
    /** MIME type of the file. */
    val type: String? = null,
    /** Image width in pixels (if applicable). */
    val width: Int? = null,
    /** Image height in pixels (if applicable). */
    val height: Int? = null,
    /** Whether the upload has failed. */
    val uploadFailed: Boolean = false,
)
