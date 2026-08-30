package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceRule
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceTest {

    private fun d(s: String) = LocalDate.parse(s)

    // ---- once ----

    @Test
    fun `once yields a single date inside the window`() {
        val rule = RecurrenceRule(RecurrenceType.ONCE, startsOn = d("2026-03-10"))
        assertEquals(
            listOf(d("2026-03-10")),
            rule.occurrencesBetween(d("2026-03-01"), d("2026-03-31")),
        )
    }

    @Test
    fun `once yields nothing when the window misses it`() {
        val rule = RecurrenceRule(RecurrenceType.ONCE, startsOn = d("2026-03-10"))
        assertTrue(rule.occurrencesBetween(d("2026-04-01"), d("2026-04-30")).isEmpty())
    }

    // ---- daily ----

    @Test
    fun `daily yields every day in the window`() {
        val rule = RecurrenceRule(RecurrenceType.DAILY, startsOn = d("2026-03-01"))
        val result = rule.occurrencesBetween(d("2026-03-01"), d("2026-03-05"))
        assertEquals(5, result.size)
        assertEquals(d("2026-03-01"), result.first())
        assertEquals(d("2026-03-05"), result.last())
    }

    @Test
    fun `daily does not start before startsOn`() {
        val rule = RecurrenceRule(RecurrenceType.DAILY, startsOn = d("2026-03-03"))
        val result = rule.occurrencesBetween(d("2026-03-01"), d("2026-03-05"))
        assertEquals(listOf(d("2026-03-03"), d("2026-03-04"), d("2026-03-05")), result)
    }

    // ---- weekly ----

    @Test
    fun `weekly twice a week yields only the chosen days`() {
        // 2026-03-02 is a Monday.
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            startsOn = d("2026-03-02"),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )
        val result = rule.occurrencesBetween(d("2026-03-02"), d("2026-03-15"))
        assertEquals(
            listOf(
                d("2026-03-02"), // Mon
                d("2026-03-05"), // Thu
                d("2026-03-09"), // Mon
                d("2026-03-12"), // Thu
            ),
            result,
        )
    }

    @Test
    fun `weekly with no days selected yields nothing`() {
        val rule = RecurrenceRule(RecurrenceType.WEEKLY, startsOn = d("2026-03-02"))
        assertTrue(rule.occurrencesBetween(d("2026-03-01"), d("2026-03-31")).isEmpty())
    }

    // ---- monthly ----

    @Test
    fun `monthly on the 31st clamps to the last day of short months`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            startsOn = d("2026-01-31"),
            dayOfMonth = 31,
        )
        val result = rule.occurrencesBetween(d("2026-01-01"), d("2026-04-30"))
        assertEquals(
            listOf(
                d("2026-01-31"),
                d("2026-02-28"), // clamped, not skipped
                d("2026-03-31"),
                d("2026-04-30"), // April has 30 days
            ),
            result,
        )
    }

    @Test
    fun `monthly on the 29th hits Feb 29 in a leap year`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            startsOn = d("2028-01-29"),
            dayOfMonth = 29,
        )
        val result = rule.occurrencesBetween(d("2028-02-01"), d("2028-02-29"))
        assertEquals(listOf(d("2028-02-29")), result)
    }

    // ---- interval ----

    @Test
    fun `interval counts from startsOn`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.INTERVAL,
            startsOn = d("2026-03-01"),
            intervalDays = 3,
        )
        val result = rule.occurrencesBetween(d("2026-03-01"), d("2026-03-12"))
        assertEquals(
            listOf(d("2026-03-01"), d("2026-03-04"), d("2026-03-07"), d("2026-03-10")),
            result,
        )
    }

    @Test
    fun `interval keeps its cadence when the window starts mid-stream`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.INTERVAL,
            startsOn = d("2026-03-01"),
            intervalDays = 3,
        )
        // Window starts on the 5th; the next valid date is the 7th, not the 5th.
        val result = rule.occurrencesBetween(d("2026-03-05"), d("2026-03-12"))
        assertEquals(listOf(d("2026-03-07"), d("2026-03-10")), result)
    }

    // ---- bounds ----

    @Test
    fun `endsOn is inclusive and truncates the series`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.DAILY,
            startsOn = d("2026-03-01"),
            endsOn = d("2026-03-03"),
        )
        val result = rule.occurrencesBetween(d("2026-03-01"), d("2026-03-10"))
        assertEquals(listOf(d("2026-03-01"), d("2026-03-02"), d("2026-03-03")), result)
    }

    @Test
    fun `stoppedOn cuts earlier than endsOn and excludes the stop day itself`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.DAILY,
            startsOn = d("2026-03-01"),
            endsOn = d("2026-03-31"),
            stoppedOn = d("2026-03-04"),
        )
        val result = rule.occurrencesBetween(d("2026-03-01"), d("2026-03-10"))
        assertEquals(listOf(d("2026-03-01"), d("2026-03-02"), d("2026-03-03")), result)
    }

    @Test
    fun `endsOn wins when it is earlier than stoppedOn`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.DAILY,
            startsOn = d("2026-03-01"),
            endsOn = d("2026-03-02"),
            stoppedOn = d("2026-03-20"),
        )
        val result = rule.occurrencesBetween(d("2026-03-01"), d("2026-03-10"))
        assertEquals(listOf(d("2026-03-01"), d("2026-03-02")), result)
    }

    @Test
    fun `inverted window yields nothing`() {
        val rule = RecurrenceRule(RecurrenceType.DAILY, startsOn = d("2026-03-01"))
        assertTrue(rule.occurrencesBetween(d("2026-03-10"), d("2026-03-01")).isEmpty())
    }

    // ---- isFinished ----

    @Test
    fun `isFinished reflects the effective end`() {
        val open = RecurrenceRule(RecurrenceType.DAILY, startsOn = d("2026-03-01"))
        assertFalse(open.isFinished(d("2030-01-01")))

        val ended = RecurrenceRule(
            type = RecurrenceType.DAILY,
            startsOn = d("2026-03-01"),
            endsOn = d("2026-03-05"),
        )
        assertFalse(ended.isFinished(d("2026-03-05")))
        assertTrue(ended.isFinished(d("2026-03-06")))
    }

    // ---- describe ----

    @Test
    fun `describe lists weekly days in order`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            startsOn = d("2026-03-02"),
            daysOfWeek = setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY),
        )
        assertEquals("Setiap Senin, Kamis", rule.describe())
    }

    // ---- the occurrence a dateless link opens ----
    //
    // A notification carries entity_id and nothing else, so tapping one has to resolve a date from
    // the rule alone. These pin the two halves of that: "next on or after today", and the fallback
    // to the most recent past one so a finished programme still opens something.

    @Test
    fun `today wins over yesterday and over tomorrow`() {
        // The off-by-one at the heart of "on or after". A daily rule has all three available.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(RecurrenceType.DAILY, startsOn = d("2026-01-01"))
        assertEquals(today, rule.occurrenceNearest(today))
    }

    @Test
    fun `a weekly program opens the next chosen day, not the last one`() {
        // Monday and Thursday; today is a Tuesday, so the answer is Thursday and not the Monday
        // that has just passed.
        val rule = RecurrenceRule(
            RecurrenceType.WEEKLY,
            startsOn = d("2026-01-01"),
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )
        val tuesday = d("2026-03-10")
        assertEquals(DayOfWeek.TUESDAY, tuesday.dayOfWeek)
        assertEquals(d("2026-03-12"), rule.occurrenceNearest(tuesday))
    }

    @Test
    fun `a program starting past the home screen's window still resolves`() {
        // ParentHomeViewModel draws 14 days ahead. A programme starting in 40 is off that list and
        // is exactly what a `tugas_baru` notification announces, so this must not reuse that bound.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(RecurrenceType.DAILY, startsOn = today.plusDays(40))
        assertEquals(today.plusDays(40), rule.occurrenceNearest(today))
    }

    @Test
    fun `a finished program opens its last day`() {
        // 40 days back, past the home screen's 30-day history window. The past side is anchored on
        // the rule's own end rather than on today, which is what makes this resolve.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(
            RecurrenceType.DAILY,
            startsOn = d("2025-01-01"),
            endsOn = today.minusDays(40),
        )
        assertEquals(today.minusDays(40), rule.occurrenceNearest(today))
    }

    @Test
    fun `a program that ended further back than the probe still opens its last day`() {
        // 1500 days, deliberately WIDER than NEAREST_PROBE_DAYS. A window anchored on today reaches
        // back only 1000 and finds nothing at all, so this is the one case that actually demands
        // the past side be anchored on the rule's own end. Verified by making that change and
        // watching this fail -- at 400 days it passed either way, which is why it says 1500.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(
            RecurrenceType.DAILY,
            startsOn = d("2018-01-01"),
            endsOn = today.minusDays(1_500),
        )
        assertEquals(today.minusDays(1_500), rule.occurrenceNearest(today))
    }

    @Test
    fun `a stopped program opens the day before it was stopped`() {
        // stoppedOn is exclusive, so stopping a daily programme today leaves yesterday as its last
        // real occurrence. Reached through this path rather than special-cased in it.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(
            RecurrenceType.DAILY,
            startsOn = d("2026-01-01"),
            stoppedOn = today,
        )
        assertEquals(today.minusDays(1), rule.occurrenceNearest(today))
    }

    @Test
    fun `a once task in the past opens its own date however old`() {
        // The reason ONCE has its own branch: 800 days is inside the probe, but the branch exists
        // so that no probe width can ever hide a one-shot task from the parent it was assigned to.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(RecurrenceType.ONCE, startsOn = today.minusDays(800))
        assertEquals(today.minusDays(800), rule.occurrenceNearest(today))
        val ancient = RecurrenceRule(RecurrenceType.ONCE, startsOn = today.minusDays(5_000))
        assertEquals(today.minusDays(5_000), ancient.occurrenceNearest(today))
    }

    @Test
    fun `a once task scheduled ahead opens its own date`() {
        val today = d("2026-03-10")
        val rule = RecurrenceRule(RecurrenceType.ONCE, startsOn = today.plusDays(5))
        assertEquals(today.plusDays(5), rule.occurrenceNearest(today))
    }

    @Test
    fun `a monthly program clamps into a short month`() {
        // Day 31 in February. The clamp lives in monthlyOccurrences and this proves it survives
        // the dateless path rather than being bypassed by it.
        val rule = RecurrenceRule(
            RecurrenceType.MONTHLY,
            startsOn = d("2026-01-31"),
            dayOfMonth = 31,
        )
        assertEquals(d("2026-02-28"), rule.occurrenceNearest(d("2026-02-01")))
    }

    @Test
    fun `the widest interval the form can produce still resolves`() {
        // CreateTaskViewModel caps the interval field at three digits, so 999 is the largest gap
        // between two occurrences this app can create. Lower NEAREST_PROBE_DAYS and this fails.
        val today = d("2026-03-10")
        val rule = RecurrenceRule(
            RecurrenceType.INTERVAL,
            startsOn = today.minusDays(1),
            intervalDays = 999,
        )
        assertEquals(today.plusDays(998), rule.occurrenceNearest(today))
    }

    @Test
    fun `a rule that can produce nothing opens nothing`() {
        val today = d("2026-03-10")
        // Each of these is a rule the UI can leave half-filled; none may invent a date.
        assertNull(RecurrenceRule(RecurrenceType.WEEKLY, startsOn = today).occurrenceNearest(today))
        assertNull(RecurrenceRule(RecurrenceType.MONTHLY, startsOn = today).occurrenceNearest(today))
        assertNull(RecurrenceRule(RecurrenceType.INTERVAL, startsOn = today).occurrenceNearest(today))
        assertNull(
            RecurrenceRule(RecurrenceType.INTERVAL, startsOn = today, intervalDays = 0)
                .occurrenceNearest(today),
        )
        assertNull(
            RecurrenceRule(RecurrenceType.DAILY, startsOn = today, endsOn = today.minusDays(1))
                .occurrenceNearest(today),
        )
    }

    @Test
    fun `a program starting beyond the probe opens nothing rather than guessing`() {
        val today = d("2026-03-10")
        val rule = RecurrenceRule(RecurrenceType.DAILY, startsOn = today.plusDays(2_000))
        assertNull(rule.occurrenceNearest(today))
    }
}
