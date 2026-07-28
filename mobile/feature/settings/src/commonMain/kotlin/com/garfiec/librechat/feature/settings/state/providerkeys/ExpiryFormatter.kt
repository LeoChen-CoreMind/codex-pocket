package com.garfiec.librechat.feature.settings.state.providerkeys

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Formats an absolute expiry [Instant][kotlin.time.Instant] as a short relative duration
 * for status banners. Mirrors web's "Expires in 11h" / "Expires in 3d" feel rather than
 * showing a raw ISO 8601 timestamp.
 *
 * Output examples:
 * - `Duration < 1 minute` → `"<1m"`
 * - `Duration < 1 hour`   → `"42m"`
 * - `Duration < 1 day`    → `"3h"`
 * - `Duration < 30 days`  → `"5d"`
 * - `Duration >= 30 days` → `"45d"` (never abbreviates beyond days; the wire model already
 *   caps the user-selectable expiry at 30d, so this is the practical ceiling).
 *
 * Past durations format as `"0m"` since `KeyState.Expired` should be used in that path
 * — this function never receives a past-expiry input in normal flow.
 *
 * Tests inject `now` rather than reading the system clock so they're hermetic.
 */
fun formatRelativeExpiry(
    expiresAt: kotlin.time.Instant,
    now: kotlin.time.Instant = Clock.System.now(),
): String {
    val remaining: Duration = expiresAt - now
    return formatDuration(remaining)
}

/** Visible for testing. */
internal fun formatDuration(duration: Duration): String {
    if (duration <= Duration.ZERO) return "0m"
    if (duration < 1.minutes) return "<1m"
    val totalMinutes = duration.inWholeMinutes
    val totalHours = duration.inWholeHours
    val totalDays = duration.inWholeDays
    return when {
        totalHours < 1L -> "${totalMinutes}m"
        totalDays < 1L -> "${totalHours}h"
        else -> "${totalDays}d"
    }
}
