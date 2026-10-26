package com.antoniusindra.sipatokkong.data.model

import java.time.LocalDate

/** One due date of a task, plus whether the parent has already completed that specific date. */
data class TaskOccurrence(
    val task: Task,
    val date: LocalDate,
    val isDone: Boolean,
) {
    /** Stable identity for LazyColumn keys — a task can appear on many dates. */
    val key: String get() = "${task.id}@$date"

    fun isOverdue(today: LocalDate): Boolean = !isDone && date < today
}

/**
 * Turns stored tasks + completions into concrete dated occurrences.
 *
 * Single source of truth for "what is due when": the parent home and the task detail screen
 * both go through here, so a one-shot task and a recurring program are treated identically.
 */
object TaskOccurrences {

    private fun priorityRank(value: String): Int = when (value) {
        "high" -> 0
        "medium" -> 1
        else -> 2
    }

    fun build(
        tasks: List<Task>,
        completions: List<TaskCompletion>,
        from: LocalDate,
        to: LocalDate,
    ): List<TaskOccurrence> {
        val doneKeys = completions.mapTo(mutableSetOf()) { it.tugasId to it.occurrenceDate }

        return tasks.filter { it.cancelledAt == null }.flatMap { task ->
            task.toRecurrenceRule().occurrencesBetween(from, to).map { date ->
                TaskOccurrence(
                    task = task,
                    date = date,
                    isDone = (task.id to date.toString()) in doneKeys,
                )
            }
        }.sortedWith(
            // Unfinished first within a day (false < true), so a completed high-priority task
            // never pushes something that still needs doing further down the list.
            compareBy(
                { it.date },
                { it.isDone },
                { priorityRank(it.task.priorityLevel) },
                { it.task.judul },
            )
        )
    }

    /** Whether one specific occurrence is already completed. */
    fun isDone(completions: List<TaskCompletion>, taskId: String, date: LocalDate): Boolean =
        completions.any { it.tugasId == taskId && it.occurrenceDate == date.toString() }
}
