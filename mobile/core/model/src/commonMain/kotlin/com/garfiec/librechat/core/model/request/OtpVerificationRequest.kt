package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Shared OTP verification body used by endpoints that optionally require a TOTP
 * token or backup code (e.g. 2FA enable, backup-code regeneration, account deletion).
 */
@Serializable
data class OtpVerificationRequest(
    val token: String? = null,
    val backupCode: String? = null,
)
