package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.ui.volunteer.VolunteerHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The numbers on the volunteer's home card.
 *
 * "Program berjalan" is the one figure the volunteer reads at a glance, so what counts towards it
 * is worth pinning down: a stopped program and a finished one both still exist as rows, and
 * counting rows would quietly overstate the workload.
 */
class VolunteerHomeStateTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 16)

    private fun task(
        id: String,
        recurrence: String = "daily",
        startsOn: String? = "2026-08-01",
        endsOn: String? = "2026-12-31",
        stoppedAt: String? = null,
        deadline: String? = null,
    ) = Task(
        id = id,
        anakId = "anak-1",
        relawanId = "relawan-1",
        judul = "Program $id",
        mediaKind = "image",
        mediaPath = "x.jpg",
        priorityLevel = "medium",
        status = "open",
        recurrence = recurrence,
        startsOn = startsOn,
        endsOn = endsOn,
        stoppedAt = stoppedAt,
        deadline = deadline,
    )

    private fun state(programs: List<Task> = emptyList(), children: List<ChildOption> = emptyList()) =
        VolunteerHomeUiState(programs = programs, children = children, today = today, isLoading = false)

    // ---- activeCount ----

    @Test
    fun `a running program counts`() {
        assertEquals(1, state(programs = listOf(task("a"))).activeCount)
    }

    @Test
    fun `a stopped program does not count`() {
        // The row is still there; the work is not.
        val stopped = task("a", stoppedAt = "2026-08-10T00:00:00Z")
        assertEquals(0, state(programs = listOf(stopped)).activeCount)
    }

    @Test
    fun `a program past its end date does not count`() {
        val expired = task("a", startsOn = "2026-01-01", endsOn = "2026-06-30")
        assertEquals(0, state(programs = listOf(expired)).activeCount)
    }

    @Test
    fun `a program ending today still counts`() {
        // Boundary: the last day is a day the parent can still be given work.
        val endsToday = task("a", startsOn = "2026-08-01", endsOn = "2026-08-16")
        assertEquals(1, state(programs = listOf(endsToday)).activeCount)
    }

    @Test
    fun `only the running ones are counted in a mixed list`() {
        val programs = listOf(
            task("running"),
            task("stopped", stoppedAt = "2026-08-10T00:00:00Z"),
            task("expired", startsOn = "2026-01-01", endsOn = "2026-06-30"),
            task("running-2"),
        )
        assertEquals(2, state(programs = programs).activeCount)
        assertEquals(listOf("running", "running-2"), state(programs = programs).activePrograms.map { it.id })
    }

    @Test
    fun `an empty list counts zero`() {
        assertEquals(0, state().activeCount)
    }

    // ---- childCount ----

    @Test
    fun `childCount reflects the assigned children`() {
        val children = listOf(ChildOption("1", "Andi"), ChildOption("2", "Siti"))
        assertEquals(2, state(children = children).childCount)
    }

    // ---- summary ----

    @Test
    fun `with nothing at all, the missing child is named and not the missing program`() {
        // The case that decides the branch order. A brand-new volunteer has neither, and telling
        // them "no programs yet" points at a form they cannot complete; the real blocker is that
        // admin has not assigned them a child. Swapping the two branches must fail here.
        assertEquals(
            "Belum ada anak yang ditugaskan kepada Anda.",
            state().summary,
        )
    }

    @Test
    fun `a volunteer with programs but no child is still told about the child`() {
        assertEquals(
            "Belum ada anak yang ditugaskan kepada Anda.",
            state(programs = listOf(task("a"))).summary,
        )
    }

    @Test
    fun `a volunteer with children but no running program is told so`() {
        assertEquals(
            "Belum ada program ajar yang berjalan.",
            state(children = listOf(ChildOption("1", "Andi"))).summary,
        )
    }

    @Test
    fun `a running count is stated plainly`() {
        assertEquals(
            "2 program ajar sedang berjalan.",
            state(
                programs = listOf(task("a"), task("b")),
                children = listOf(ChildOption("1", "Andi")),
            ).summary,
        )
    }
}
