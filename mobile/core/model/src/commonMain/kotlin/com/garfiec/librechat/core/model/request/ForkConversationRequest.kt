package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

@Serializable
data class ForkConversationRequest(
    val conversationId: String,
    val messageId: String,
    val option: String? = null,
    val splitAtTarget: Boolean? = null,
    val latestMessageId: String? = null,
)

/**
 * Maps to the backend ForkOptions enum values.
 * - DIRECT_PATH: Only the direct path of messages to the target
 * - INCLUDE_BRANCHES: Direct path plus sibling messages at each level
 * - TARGET_LEVEL: All messages and branches up to the target level (default)
 */
object ForkOption {
    const val DIRECT_PATH = "directPath"
    const val INCLUDE_BRANCHES = "includeBranches"
    const val TARGET_LEVEL = "targetLevel"
}
