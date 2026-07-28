package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable

/**
 * Response of upstream `GET /api/agents/tools/:toolId/auth`
 * (controllers/tools.js:62). `message` carries an [AuthType] wire value when
 * authenticated -- `user_provided` means the user has installed their own key,
 * `system_defined` means the server has a key configured for the user.
 */
@Serializable
data class ToolAuthResult(
    val authenticated: Boolean? = null,
    val message: String? = null,
) {
    val isUserProvided: Boolean get() = message == AuthType.USER_PROVIDED
    val isSystemDefined: Boolean get() = message == AuthType.SYSTEM_DEFINED
}

/** Upstream `AuthType` wire values (schemas.ts:10). */
object AuthType {
    const val OVERRIDE_AUTH = "override_auth"
    const val USER_PROVIDED = "user_provided"
    const val SYSTEM_DEFINED = "system_defined"
}
