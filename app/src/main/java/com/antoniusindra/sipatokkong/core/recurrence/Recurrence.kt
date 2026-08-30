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
     * The single occurrence a link carrying no date should open: the next one on or after [today],
     * and failing that the most recent one before it.
     *
     * Exists because a notification row carries only `entity_id`. `notifications` has no date
     * column and cannot gain one usefully -- a `tugas_baru` row is written when the programme is
     * created, which is rarely the day any of its occurrences fall on.
     *
     * Lives on the rule rather than beside `TaskOccurrences` because the answer depends on
     * [effectiveEnd], which is private and encodes the `stoppedOn - 1` exclusivity. A helper
     * outside this class would have to re-derive that and would drift from it.
     *
     * Deliberately does **not** reuse the parent home's `PAST_WINDOW_DAYS` / `FUTURE_WINDOW_DAYS`.
     * Those bound what a *list* draws; a programme starting in three weeks is off that list while
     * being exactly the thing the notification announced.
     *
     * Returns null only when this rule can produce no occurrence at all in reach: a weekly rule
     * with no chosen days, a monthly one with no day of month, an interval with no step, an end
     * before the start, or a programme starting beyond the probe. The caller shows an error rather
     * than inventing a date.
     */
    fun occurrenceNearest(today: LocalDate, probeDays: Long = NEAREST_PROBE_DAYS): LocalDate? {
        // ONCE has exactly one candidate and it *is* the task, however old. No probe window may be
        // allowed to hide it: a one-shot task filed two years ago must still open. Asking
        // occurrencesBetween for that single day keeps the endsOn/stoppedOn clamp applied here too.
        if (type == RecurrenceType.ONCE) return occurrencesBetween(startsOn, startsOn).firstOrNull()

        // The past side is anchored on the rule's own end rather than on today, so a programme that
        // finished long ago resolves to its final date instead of falling off the back of the probe.
        val pastAnchor = effectiveEnd?.let { minOf(today, it) } ?: today
        val from = maxOf(startsOn, pastAnchor.minusDays(probeDays))
        val to = today.plusDays(probeDays)

        // One call, and deliberately not a hand-rolled "walk forward until you find one". Every
        // cadence's arithmetic -- the interval anchoring, the short-month clamp, both range bounds
        // -- already lives in occurrencesBetween and is already tested. Materialising a couple of
        // thousand LocalDates once, on a notification tap, is the cheaper mistake to make.
        val dates = occurrencesBetween(from, to)
        return dates.firstOrNull { it >= today } ?: dates.lastOrNull()
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

    companion object {
        /**
         * How far either side [occurrenceNearest] looks, in days.
         *
         * 1000 because `CreateTaskViewModel.onIntervalChange` caps the interval field with
         * `take(3)`, so 999 days is the widest gap between two occurrences this app can create;
         * monthly is at most 31 and weekly at most 7. One probe of this size therefore cannot step
         * over an occurrence of any programme the form can produce.
         */
        const val NEAREST_PROBE_DAYS = 1_000L
    }

}
