package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/** History id is the completion id; overdue id is the task id. Both use a date/id cursor. */
@Serializable
data class TaskPageRow(
    val id: String,
    @SerialName("occurrence_date") val occurrenceDate: String,
    val task: Task,
) {
    fun occurrence(done: Boolean) = TaskOccurrence(task, LocalDate.parse(occurrenceDate), done)
    val key: String get() = "$occurrenceDate@$id"
}
