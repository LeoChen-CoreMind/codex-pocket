package com.garfiec.librechat.core.common.extensions

/**
 * Returns a locale-aware full month name + year string (e.g. "January 2025" in English).
 */
expect fun formatMonthYear(month: Int, year: Int): String

/**
 * Returns a locale-aware abbreviated month name (e.g. "Jan" in English).
 */
expect fun formatMonthAbbrev(month: Int): String
