package com.garfiec.librechat.feature.chat.components.artifact

/**
 * Parsed artifact extracted from message content. Artifacts are marked in LLM
 * responses using the remark-directive markdown format:
 *
 * ```
 * :::artifact{identifier="id" type="mime-type" title="Title"}
 * ```language
 * ...content...
 * ```
 * :::
 * ```
 *
 * Supported types: text/html, image/svg+xml, application/vnd.react,
 * application/vnd.mermaid, text/markdown, text/md, text/plain,
 * application/vnd.code-html.
 */
data class Artifact(
    val identifier: String,
    val type: String,
    val title: String,
    val language: String?,
    val content: String,
    val version: Int = 1,
)
