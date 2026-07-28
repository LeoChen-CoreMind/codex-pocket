package com.garfiec.librechat.core.common.extensions

import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

actual fun formatMonthYear(month: Int, year: Int): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.dateFormat = "MMMM yyyy"
    val components = NSDateComponents()
    components.month = month.toLong()
    components.year = year.toLong()
    components.day = 1
    val calendar = NSCalendar.currentCalendar
    val date = calendar.dateFromComponents(components) ?: return ""
    return formatter.stringFromDate(date)
}

actual fun formatMonthAbbrev(month: Int): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale.currentLocale
    formatter.dateFormat = "MMM"
    val components = NSDateComponents()
    components.month = month.toLong()
    components.year = 2000
    components.day = 1
    val calendar = NSCalendar.currentCalendar
    val date = calendar.dateFromComponents(components) ?: return ""
    return formatter.stringFromDate(date)
}
