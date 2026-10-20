package com.borgeiz.meutcc2026.util

fun formatDateDisplay(stored: String): String {
    val parts = stored.trim().split('-')
    if (parts.size != 3) return stored
    return "${parts[2]}/${parts[1]}/${parts[0]}"
}

fun formatDateDisplay(year: Int, month: Int, day: Int): String =
    "%02d/%02d/%d".format(day, month, year)

fun toStoredDate(display: String): String {
    val parts = splitDisplayDate(display) ?: return display.trim()
    return "${parts[0]}-${parts[1]}-${parts[2]}"
}

fun splitDisplayDate(display: String?): List<String>? {
    val text = display?.trim() ?: return null
    return if (text.contains('/')) {
        text.split('/').takeIf { it.size == 3 }?.let { (d, m, y) -> listOf(y, m, d) }
    } else {
        text.split('-').takeIf { it.size == 3 }
    }
}
