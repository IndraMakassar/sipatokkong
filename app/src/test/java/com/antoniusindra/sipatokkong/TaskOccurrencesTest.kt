package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskCompletion
import com.antoniusindra.sipatokkong.data.model.TaskOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Covers what the parent actually sees on the home screen: which occurrences exist, whether they
 * are marked done, and in what order they are listed.
 */
class TaskOccurrencesTest {

    private fun d(s: String) = LocalDate.parse(s)

    private fun task(
        id: String,
        judul: String,
        priority: String = "medium",
        deadline: String? = null,
        recurrence: String = "once",
        days: List<Int>? = null,
        startsOn: String? = null,
        endsOn: String? = null,
    ) = Task(
        id = id,
        anakId = "anak-1",
        relawanId = "relawan-1",
        judul = judul,
        mediaKind = "image",
        mediaPath = "path/$id.jpg",
        priorityLevel = priority,
        deadline = deadline,
        status = "assigned",
        recurrence = recurrence,
        recurrenceDays = days,
        startsOn = startsOn,
        endsOn = endsOn,
    )

    // ---- ordering ----

    @Test
    fun `unfinished comes before finished on the same day even when finished has higher priority`() {
        val done = task("1", "Sudah Selesai", priority = "high", deadline = "2026-03-10")
        val pending = task("2", "Belum Selesai", priority = "medium", deadline = "2026-03-10")

        val result = TaskOccurrences.build(
            tasks = listOf(done, pending),
            completions = listOf(TaskCompletion(tugasId = "1", occurrenceDate = "2026-03-10")),
            from = d("2026-03-10"),
            to = d("2026-03-10"),
        )

        assertEquals(listOf("Belum Selesai", "Sudah Selesai"), result.map { it.task.judul })
    }

    @Test
    fun `priority still orders unfinished occurrences within a day`() {
        val low = task("1", "Rendah", priority = "low", deadline = "2026-03-10")
        val high = task("2", "Tinggi", priority = "high", deadline = "2026-03-10")
        val medium = task("3", "Sedang", priority = "medium", deadline = "2026-03-10")

        val result = TaskOccurrences.build(
            tasks = listOf(low, high, medium),
            completions = emptyList(),
            from = d("2026-03-10"),
            to = d("2026-03-10"),
        )

        assertEquals(listOf("Tinggi", "Sedang", "Rendah"), result.map { it.task.judul })
    }

    @Test
    fun `dates stay in ascending order regardless of done state`() {
        // Daily program: 10th completed, 11th and 12th still open.
        val daily = task(
            id = "1",
            judul = "Latihan Harian",
            recurrence = "daily",
            startsOn = "2026-03-10",
            endsOn = "2026-03-12",
        )

        val result = TaskOccurrences.build(
            tasks = listOf(daily),
            completions = listOf(TaskCompletion(tugasId = "1", occurrenceDate = "2026-03-10")),
            from = d("2026-03-10"),
            to = d("2026-03-12"),
        )

        assertEquals(
            listOf(d("2026-03-10"), d("2026-03-11"), d("2026-03-12")),
            result.map { it.date },
        )
    }

    // ---- completion matching ----

    @Test
    fun `completion matches on both task id and occurrence date`() {
        val daily = task(
            id = "1",
            judul = "Latihan Harian",
            recurrence = "daily",
            startsOn = "2026-03-10",
            endsOn = "2026-03-12",
        )

        val result = TaskOccurrences.build(
            tasks = listOf(daily),
            completions = listOf(TaskCompletion(tugasId = "1", occurrenceDate = "2026-03-11")),
            from = d("2026-03-10"),
            to = d("2026-03-12"),
        )

        // Only the 11th is done — this is the whole point of per-occurrence completion.
        assertEquals(mapOf(d("2026-03-10") to false, d("2026-03-11") to true, d("2026-03-12") to false),
            result.associate { it.date to it.isDone })
    }

    @Test
    fun `a completion for a different task does not mark this one done`() {
        val mine = task("1", "Punya Saya", deadline = "2026-03-10")

        val result = TaskOccurrences.build(
            tasks = listOf(mine),
            completions = listOf(TaskCompletion(tugasId = "2", occurrenceDate = "2026-03-10")),
            from = d("2026-03-10"),
            to = d("2026-03-10"),
        )

        assertEquals(1, result.size)
        assertFalse(result.first().isDone)
    }

    // ---- occurrence identity & helpers ----

    @Test
    fun `key is unique per task and date so list items stay distinct`() {
        val daily = task(
            id = "1",
            judul = "Latihan Harian",
            recurrence = "daily",
            startsOn = "2026-03-10",
            endsOn = "2026-03-12",
        )

        val keys = TaskOccurrences.build(
            tasks = listOf(daily),
            completions = emptyList(),
            from = d("2026-03-10"),
            to = d("2026-03-12"),
        ).map { it.key }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `isOverdue is true only for past unfinished occurrences`() {
        val today = d("2026-03-11")
        val past = task("1", "Kemarin", deadline = "2026-03-10")
        val future = task("2", "Besok", deadline = "2026-03-12")

        val result = TaskOccurrences.build(
            tasks = listOf(past, future),
            completions = emptyList(),
            from = d("2026-03-10"),
            to = d("2026-03-12"),
        ).associateBy { it.task.judul }

        assertTrue(result.getValue("Kemarin").isOverdue(today))
        assertFalse(result.getValue("Besok").isOverdue(today))
    }

    @Test
    fun `a completed past occurrence is not overdue`() {
        val past = task("1", "Kemarin", deadline = "2026-03-10")

        val occurrence = TaskOccurrences.build(
            tasks = listOf(past),
            completions = listOf(TaskCompletion(tugasId = "1", occurrenceDate = "2026-03-10")),
            from = d("2026-03-10"),
            to = d("2026-03-10"),
        ).first()

        assertFalse(occurrence.isOverdue(d("2026-03-11")))
    }

    // ---- isDone lookup helper ----

    @Test
    fun `isDone helper matches the same task and date pair`() {
        val completions = listOf(TaskCompletion(tugasId = "1", occurrenceDate = "2026-03-10"))

        assertTrue(TaskOccurrences.isDone(completions, "1", d("2026-03-10")))
        assertFalse(TaskOccurrences.isDone(completions, "1", d("2026-03-11")))
        assertFalse(TaskOccurrences.isDone(completions, "2", d("2026-03-10")))
    }
}
