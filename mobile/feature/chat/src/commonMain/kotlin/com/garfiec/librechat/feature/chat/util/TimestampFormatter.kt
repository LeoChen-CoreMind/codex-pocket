package com.garfiec.librechat.feature.chat.util

/**
 * Formats an ISO 8601 timestamp into a relative time string (e.g. "2m ago").
 */
expect fun formatRelativeTimestamp(isoTimestamp: String): String

/**
 * Formats an ISO 8601 timestamp into an absolute date string (e.g. "Mar 29, 2026 3:45 PM").
 */
expect fun formatAbsoluteTimestamp(isoTimestamp: String): String
