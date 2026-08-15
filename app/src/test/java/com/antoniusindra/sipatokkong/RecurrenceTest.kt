package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceRule
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
