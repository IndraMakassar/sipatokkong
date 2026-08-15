package com.antoniusindra.sipatokkong.core.recurrence

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Recurrence rules for a program ajar.
 *
 * Deliberately free of Android and Supabase dependencies so the date maths — the part most
 * likely to be wrong — can be covered by plain JVM unit tests.
 */
enum class RecurrenceType(val dbValue: String, val label: String) {
    ONCE("once", "Sekali saja"),
    DAILY("daily", "Setiap hari"),
    WEEKLY("weekly", "Mingguan"),
    MONTHLY("monthly", "Bulanan"),
    INTERVAL("interval", "Tiap N hari");

    companion object {
        fun fromDb(value: String?): RecurrenceType =
            entries.firstOrNull { it.dbValue == value } ?: ONCE
    }
}

/** Indonesian day labels, indexed by ISO day-of-week (1 = Monday). */
val DayOfWeek.labelId: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "Senin"
        DayOfWeek.TUESDAY -> "Selasa"
        DayOfWeek.WEDNESDAY -> "Rabu"
        DayOfWeek.THURSDAY -> "Kamis"
        DayOfWeek.FRIDAY -> "Jumat"
        DayOfWeek.SATURDAY -> "Sabtu"
        DayOfWeek.SUNDAY -> "Minggu"
    }

data class RecurrenceRule(
    val type: RecurrenceType,
    val startsOn: LocalDate,
    val endsOn: LocalDate? = null,
    /** Only meaningful for [RecurrenceType.WEEKLY]. */
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    /** Only meaningful for [RecurrenceType.MONTHLY]; 1..31. */
    val dayOfMonth: Int? = null,
    /** Only meaningful for [RecurrenceType.INTERVAL]; every N days counted from [startsOn]. */
    val intervalDays: Int? = null,
    /** Date the volunteer stopped the program; no occurrences fall on or after it. */
    val stoppedOn: LocalDate? = null,
) {

    /**
     * The last date this rule can still produce an occurrence for.
     *
     * `endsOn` is inclusive. `stoppedOn` is exclusive — stopping a program today should not
     * silently keep today's occurrence alive, so the effective last day is the day before.
     * Whichever comes first wins; null on both sides means "no limit from that side".
     */
    private val effectiveEnd: LocalDate?
        get() {
            val stopBound = stoppedOn?.minusDays(1)
            return when {
                endsOn == null -> stopBound
                stopBound == null -> endsOn
                else -> minOf(endsOn, stopBound)
            }
        }

    /** True when the program has no more occurrences after [today]. */
    fun isFinished(today: LocalDate): Boolean {
        val end = effectiveEnd ?: return false
        return end < today
    }

    /**
     * Every occurrence date in `[from, to]` (both inclusive), ascending.
     *
     * Returns an empty list when the window falls entirely outside the rule's active range.
     */
    fun occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to < from) return emptyList()

        // Clamp the requested window to the rule's own active range.
        val windowStart = maxOf(from, startsOn)
        val windowEnd = effectiveEnd?.let { minOf(to, it) } ?: to
        if (windowEnd < windowStart) return emptyList()

        return when (type) {
            RecurrenceType.ONCE ->
                if (startsOn in windowStart..windowEnd) listOf(startsOn) else emptyList()

            RecurrenceType.DAILY ->
                generateDates(windowStart, windowEnd) { it.plusDays(1) }

            RecurrenceType.WEEKLY -> {
                if (daysOfWeek.isEmpty()) return emptyList()
                generateDates(windowStart, windowEnd) { it.plusDays(1) }
                    .filter { it.dayOfWeek in daysOfWeek }
            }

            RecurrenceType.MONTHLY -> monthlyOccurrences(windowStart, windowEnd)

            RecurrenceType.INTERVAL -> {
                val step = intervalDays ?: return emptyList()
                if (step < 1) return emptyList()
                // Counted from startsOn so the cadence never drifts, regardless of the window.
                val daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(startsOn, windowStart)
                val stepsToSkip = ((daysFromStart + step - 1) / step).coerceAtLeast(0)
                var cursor = startsOn.plusDays(stepsToSkip * step)
                buildList {
                    while (cursor <= windowEnd) {
                        if (cursor >= windowStart) add(cursor)
                        cursor = cursor.plusDays(step.toLong())
                    }
                }
            }
        }
    }

    /**
     * One occurrence per month on [dayOfMonth].
     *
     * A month that is too short (e.g. the 31st in February) is **clamped to its last day**
     * rather than skipped — a therapy program should never lose a whole month.
     */
    private fun monthlyOccurrences(windowStart: LocalDate, windowEnd: LocalDate): List<LocalDate> {
        val target = dayOfMonth ?: return emptyList()
        if (target !in 1..31) return emptyList()

        return buildList {
            var month = windowStart.withDayOfMonth(1)
            while (month <= windowEnd) {
                val day = target.coerceAtMost(month.lengthOfMonth())
                val candidate = month.withDayOfMonth(day)
                if (candidate in windowStart..windowEnd) add(candidate)
                month = month.plusMonths(1)
            }
        }
    }

    private inline fun generateDates(
        from: LocalDate,
        to: LocalDate,
        next: (LocalDate) -> LocalDate,
    ): List<LocalDate> = buildList {
        var cursor = from
        while (cursor <= to) {
            add(cursor)
            cursor = next(cursor)
        }
    }

    /** Short human summary, e.g. "Setiap Senin, Kamis" — used on cards and list rows. */
    fun describe(): String = when (type) {
        RecurrenceType.ONCE -> "Sekali saja"
        RecurrenceType.DAILY -> "Setiap hari"
        RecurrenceType.WEEKLY ->
            if (daysOfWeek.isEmpty()) "Mingguan"
            else "Setiap " + daysOfWeek.sortedBy { it.value }.joinToString(", ") { it.labelId }
        RecurrenceType.MONTHLY -> "Setiap tanggal ${dayOfMonth ?: "-"}"
        RecurrenceType.INTERVAL -> "Tiap ${intervalDays ?: "-"} hari"
    }
}
