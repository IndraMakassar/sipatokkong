package com.antoniusindra.sipatokkong.core.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "29 Agu 2026". The one short-date format; five near-identical copies of it existed before. */
val ShortDateId: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

/**
 * A date as it should read on screen, or null when there is nothing usable to show.
 *
 * Recent days are named rather than dated, matching `dateHeaderLabel` on the parent's home: for a
 * log you scan, "Hari Ini" carries more than "29 Agu 2026" does.
 *
 * Returns **null** rather than a placeholder for a missing or unparseable value, so the caller can
 * drop the line entirely instead of printing something meaningless.
 */
fun formatDisplayDate(date: LocalDate?, today: LocalDate): String? = when (date) {
    null -> null
    today -> "Hari Ini"
    today.minusDays(1) -> "Kemarin"
    else -> date.format(ShortDateId)
}

/**
 * The same, for a raw Postgres timestamp string.
 *
 * Timestamps arrive as `String` throughout this app (`Feedback.createdAt`, `FeedbackReply
 * .createdAt`, `Task.createdAt`), and the only handling they ever got was `take(10)`, which put a
 * bare ISO `2026-08-29` on screen. Anything that will not parse is treated as absent -- a display
 * helper must never take a screen down over a stored value.
 */
fun formatIsoDate(iso: String?, today: LocalDate): String? {
    val date = iso?.takeIf { it.isNotBlank() }
        ?.take(10)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return formatDisplayDate(date, today)
}
