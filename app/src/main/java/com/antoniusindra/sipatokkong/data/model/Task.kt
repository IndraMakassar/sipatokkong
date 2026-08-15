package com.antoniusindra.sipatokkong.data.model

import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceRule
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/** UI priority option, mapped to the Postgres enum `task_priority`. */
enum class TaskPriority(val dbValue: String, val label: String) {
    LOW("low", "Rendah"),
    MEDIUM("medium", "Sedang"),
    HIGH("high", "Tinggi"),
}

/** Media kind, mapped to the Postgres enum `media_kind`. */
enum class MediaKind(val dbValue: String) {
    IMAGE("image"),
    VIDEO("video"),
}

/**
 * Insert payload for `public.tugas`. Omits id / status / created_at (DB defaults).
 * `media_path` is required by both the schema (NOT NULL) and the RLS insert policy.
 *
 * Recurrence fields default to a one-shot task, matching the column defaults.
 */
@Serializable
data class TaskInsert(
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
    val judul: String,
    val instruksi: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("priority_level") val priorityLevel: String,
    val deadline: String? = null,
    @SerialName("week_of") val weekOf: String? = null,
    val recurrence: String = "once",
    @SerialName("recurrence_days") val recurrenceDays: List<Int>? = null,
    @SerialName("recurrence_day_of_month") val recurrenceDayOfMonth: Int? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerialName("starts_on") val startsOn: String? = null,
    @SerialName("ends_on") val endsOn: String? = null,
)

/** Update payload used to stop a recurring program. */
@Serializable
data class TaskStopUpdate(
    @SerialName("stopped_at") val stoppedAt: String,
)

/** A row of `public.tugas` (read model). */
@Serializable
data class Task(
    val id: String,
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
    val judul: String,
    val instruksi: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("media_thumb_path") val mediaThumbPath: String? = null,
    @SerialName("priority_level") val priorityLevel: String,
    val deadline: String? = null,
    @SerialName("week_of") val weekOf: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    val recurrence: String = "once",
    @SerialName("recurrence_days") val recurrenceDays: List<Int>? = null,
    @SerialName("recurrence_day_of_month") val recurrenceDayOfMonth: Int? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerialName("starts_on") val startsOn: String? = null,
    @SerialName("ends_on") val endsOn: String? = null,
    @SerialName("stopped_at") val stoppedAt: String? = null,
) {
    val recurrenceType: RecurrenceType get() = RecurrenceType.fromDb(recurrence)

    val isRecurring: Boolean get() = recurrenceType != RecurrenceType.ONCE

    val isStopped: Boolean get() = stoppedAt != null

    /**
     * The rule describing when this program is due.
     *
     * A one-shot task anchors on its `deadline`, falling back to `starts_on` and then to the
     * creation date, so legacy rows created before this feature still produce one occurrence.
     */
    fun toRecurrenceRule(): RecurrenceRule {
        val anchor = parseDate(startsOn)
            ?: parseDate(deadline)
            ?: createdAt?.take(10)?.let { parseDate(it) }
            ?: LocalDate.now()

        val onceAnchor = parseDate(deadline) ?: anchor

        return RecurrenceRule(
            type = recurrenceType,
            startsOn = if (recurrenceType == RecurrenceType.ONCE) onceAnchor else anchor,
            endsOn = parseDate(endsOn),
            daysOfWeek = recurrenceDays.orEmpty()
                .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }
                .toSet(),
            dayOfMonth = recurrenceDayOfMonth,
            intervalDays = recurrenceInterval,
            stoppedOn = stoppedAt?.take(10)?.let { parseDate(it) },
        )
    }

    private fun parseDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}
