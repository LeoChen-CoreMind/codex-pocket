package com.garfiec.librechat.core.common.extensions

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

actual fun formatMonthYear(month: Int, year: Int): String {
    val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$monthName $year"
}

actual fun formatMonthAbbrev(month: Int): String =
    Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
