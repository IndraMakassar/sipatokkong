package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import com.antoniusindra.sipatokkong.data.model.TaskTemplate
import com.antoniusindra.sipatokkong.data.model.TaskTemplateInsert
import com.antoniusindra.sipatokkong.ui.volunteer.CreateTaskUiState
import com.antoniusindra.sipatokkong.ui.volunteer.applyTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The line between "the exercise" and "this delivery of it".
 *
 * A template exists so a volunteer stops retyping the same latihan for a different child. That only
 * works if the fields describing *who* and *when* — `anakId`, `deadline`, `startsOn`, `endsOn` —
 * never travel, in either direction. Both halves of that rule are pure functions precisely so this
 * test can hold them to it.
 */
class TaskTemplateTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 29)

    private fun task(
        recurrence: String = "weekly",
        recurrenceDays: List<Int>? = listOf(1, 4),
        alatBantu: List<String>? = listOf("cermin", "sedotan"),
    ) = Task(
        id = "tugas-1",
        anakId = "anak-lama",
        relawanId = "relawan-1",
        judul = "Latihan Vokal A-I-U",
        instruksi = "### Langkah\n- Duduk berhadapan",
        mediaKind = "video",
        mediaPath = "anak-lama/2026/08/demo.mp4",
        priorityLevel = "high",
        deadline = "2026-09-01",
        weekOf = "2026-08-24",
        status = "assigned",
        recurrence = recurrence,
        recurrenceDays = recurrenceDays,
        recurrenceDayOfMonth = null,
        recurrenceInterval = null,
        startsOn = "2026-08-01",
        endsOn = "2026-12-31",
        stoppedAt = "2026-08-20T00:00:00Z",
        alatBantu = alatBantu,
    )

    private fun template(
        recurrence: String = "weekly",
        recurrenceDays: List<Int>? = listOf(1, 4),
        recurrenceDayOfMonth: Int? = null,
        recurrenceInterval: Int? = null,
        priorityLevel: String = "high",
    ) = TaskTemplate(
        id = "tpl-1",
        relawanId = "relawan-1",
        nama = "Vokal Mingguan",
        judul = "Latihan Vokal A-I-U",
        instruksi = "### Langkah\n- Duduk berhadapan",
        mediaKind = "video",
        mediaPath = "relawan-1/2026/08/demo.mp4",
        priorityLevel = priorityLevel,
        alatBantu = listOf("cermin", "sedotan"),
        recurrence = recurrence,
        recurrenceDays = recurrenceDays,
        recurrenceDayOfMonth = recurrenceDayOfMonth,
        recurrenceInterval = recurrenceInterval,
    )

    // ---------------------------------------------------------------- task -> template

    @Test
    fun `saving a program as a template drops the child, the dates and the status`() {
        val insert = TaskTemplateInsert.from(
            task = task(),
            nama = "Vokal Mingguan",
            relawanId = "relawan-1",
            mediaPath = "relawan-1/2026/08/demo.mp4",
        )

        // Nothing about *this* delivery may survive. These are separate fields on Task, and every
        // one of them is absent from TaskTemplateInsert by construction — this asserts the type
        // still has no room for them by checking what the insert actually carries.
        val fields = insert.toString()
        assertTrue("child id leaked: $fields", !fields.contains("anak-lama"))
        assertTrue("deadline leaked: $fields", !fields.contains("2026-09-01"))
        assertTrue("starts_on leaked: $fields", !fields.contains("2026-08-01"))
        assertTrue("ends_on leaked: $fields", !fields.contains("2026-12-31"))
        assertTrue("week_of leaked: $fields", !fields.contains("2026-08-24"))
        assertTrue("stopped_at leaked: $fields", !fields.contains("2026-08-20"))
    }

    @Test
    fun `saving a program as a template keeps the exercise itself`() {
        val insert = TaskTemplateInsert.from(
            task = task(),
            nama = "  Vokal Mingguan  ",
            relawanId = "relawan-1",
            mediaPath = "relawan-1/2026/08/demo.mp4",
        )

        assertEquals("Vokal Mingguan", insert.nama)   // trimmed
        assertEquals("Latihan Vokal A-I-U", insert.judul)
        assertEquals("### Langkah\n- Duduk berhadapan", insert.instruksi)
        assertEquals("high", insert.priorityLevel)
        assertEquals(listOf("cermin", "sedotan"), insert.alatBantu)
        assertEquals("weekly", insert.recurrence)
        assertEquals(listOf(1, 4), insert.recurrenceDays)
    }

    @Test
    fun `the template stores the copied path, never the task's own`() {
        // The task's media lives under a child id; storing that would make the template unreadable
        // to any volunteer not assigned to that child — the exact case sharing has to survive.
        val insert = TaskTemplateInsert.from(
            task = task(),
            nama = "Vokal",
            relawanId = "relawan-1",
            mediaPath = "relawan-1/2026/08/copied.mp4",
        )
        assertEquals("relawan-1/2026/08/copied.mp4", insert.mediaPath)
    }

    @Test
    fun `an empty aid list is stored as null, not an empty array`() {
        val insert = TaskTemplateInsert.from(
            task = task(alatBantu = emptyList()),
            nama = "Vokal",
            relawanId = "relawan-1",
            mediaPath = "p.mp4",
        )
        assertNull(insert.alatBantu)
    }

    // ---------------------------------------------------------------- template -> form

    @Test
    fun `applying a template fills the exercise`() {
        val state = CreateTaskUiState(isLoadingChildren = false).applyTemplate(template(), today)

        assertEquals("Latihan Vokal A-I-U", state.judul)
        assertEquals("### Langkah\n- Duduk berhadapan", state.instruksi)
        assertEquals(TaskPriority.HIGH, state.priority)
        assertEquals(setOf("cermin", "sedotan"), state.alatBantu)
        assertEquals(MediaKind.VIDEO, state.mediaKind)
        assertEquals("relawan-1/2026/08/demo.mp4", state.templateMediaPath)
        assertEquals(RecurrenceType.WEEKLY, state.recurrence)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), state.daysOfWeek)
        assertTrue(state.hasMedia)
    }

    @Test
    fun `applying a template leaves the child and the dates for the volunteer`() {
        val before = CreateTaskUiState(
            isLoadingChildren = false,
            children = listOf(ChildOption(id = "anak-baru", nama = "Budi")),
            selectedAnakId = "anak-baru",
            deadline = LocalDate.of(2026, 9, 30),
            endsOn = LocalDate.of(2026, 10, 31),
        )

        val after = before.applyTemplate(template(), today)

        assertEquals("anak-baru", after.selectedAnakId)
        assertEquals(before.children, after.children)
        assertEquals(LocalDate.of(2026, 9, 30), after.deadline)
        assertEquals(LocalDate.of(2026, 10, 31), after.endsOn)
    }

    @Test
    fun `a recurring template starts today unless a date is already chosen`() {
        // Same rule as picking a repeating pattern by hand in onRecurrenceChange.
        val fresh = CreateTaskUiState(isLoadingChildren = false).applyTemplate(template(), today)
        assertEquals(today, fresh.startsOn)

        val chosen = CreateTaskUiState(isLoadingChildren = false, startsOn = LocalDate.of(2026, 9, 7))
            .applyTemplate(template(), today)
        assertEquals(LocalDate.of(2026, 9, 7), chosen.startsOn)

        // `endsOn` is required for a recurring program, so guessing it would put a date in front of
        // the volunteer that nobody decided on.
        assertNull(fresh.endsOn)
    }

    @Test
    fun `a once template leaves no recurrence leftovers behind`() {
        // The form the template lands on is already weekly with days ticked. Without clearing them,
        // the chips would still be selected underneath and would reappear the moment the volunteer
        // switched back to weekly.
        val weeklyForm = CreateTaskUiState(
            isLoadingChildren = false,
            recurrence = RecurrenceType.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
            dayOfMonth = "15",
            intervalDays = "3",
        )

        val after = weeklyForm.applyTemplate(template(recurrence = "once", recurrenceDays = null), today)

        assertEquals(RecurrenceType.ONCE, after.recurrence)
        assertEquals(emptySet<DayOfWeek>(), after.daysOfWeek)
        assertEquals("", after.dayOfMonth)
        assertEquals("", after.intervalDays)
    }

    @Test
    fun `each recurrence type only carries its own sub-field`() {
        val monthly = CreateTaskUiState(isLoadingChildren = false).applyTemplate(
            template(recurrence = "monthly", recurrenceDays = listOf(1), recurrenceDayOfMonth = 15),
            today,
        )
        assertEquals("15", monthly.dayOfMonth)
        assertEquals(emptySet<DayOfWeek>(), monthly.daysOfWeek)   // stale weekly days dropped
        assertEquals("", monthly.intervalDays)

        val interval = CreateTaskUiState(isLoadingChildren = false).applyTemplate(
            template(recurrence = "interval", recurrenceDays = listOf(1), recurrenceInterval = 3),
            today,
        )
        assertEquals("3", interval.intervalDays)
        assertEquals(emptySet<DayOfWeek>(), interval.daysOfWeek)
        assertEquals("", interval.dayOfMonth)
    }

    @Test
    fun `applying a template clears a picked file and a spent aid filter`() {
        val dirty = CreateTaskUiState(
            isLoadingChildren = false,
            mediaUri = "content://picked",
            mediaName = "Media terpilih (.jpg)",
            aidQuery = "bol",
        )

        val after = dirty.applyTemplate(template(), today)

        // The two media sources are mutually exclusive; leaving both set would make submit() upload
        // the old file instead of copying the template's.
        assertNull(after.mediaUri)
        assertNull(after.mediaName)
        assertEquals("", after.aidQuery)
    }

    @Test
    fun `an unknown priority falls back rather than crashing`() {
        val after = CreateTaskUiState(isLoadingChildren = false)
            .applyTemplate(template(priorityLevel = "urgent"), today)
        assertEquals(TaskPriority.MEDIUM, after.priority)
    }

    @Test
    fun `describeRecurrence reads the pattern without needing any date`() {
        assertEquals("Setiap Senin, Kamis", template().describeRecurrence())
        assertEquals("Sekali saja", template(recurrence = "once").describeRecurrence())
        assertEquals(
            "Setiap tanggal 15",
            template(recurrence = "monthly", recurrenceDayOfMonth = 15).describeRecurrence(),
        )
        assertEquals(
            "Tiap 3 hari",
            template(recurrence = "interval", recurrenceInterval = 3).describeRecurrence(),
        )
    }
}
