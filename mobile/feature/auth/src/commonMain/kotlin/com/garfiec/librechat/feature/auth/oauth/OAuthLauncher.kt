package com.garfiec.librechat.feature.auth.oauth

/**
 * Platform-abstracted OAuth launcher.
 * Android: Chrome Custom Tabs + CookieManager
 * iOS: ASWebAuthenticationSession
 */
interface OAuthLauncher {
    /** Open the OAuth flow for the given provider. */
    fun launchOAuth(provider: String, serverUrl: String)

    /** After OAuth redirect, extract refresh token from cookies/callback. */
    fun extractTokenFromCookies(serverUrl: String): String?

    /** Clear OAuth cookie/state after successful extraction. */
    fun clearOAuthCookie(serverUrl: String)
}
