package com.antoniusindra.sipatokkong.data.model

import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceRule
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A row of `public.tugas_template` — a reusable recipe for a program ajar.
 *
 * Compare this field-for-field with [Task]: what is **missing** is the whole point. There is no
 * `anakId`, no `deadline`, no `startsOn`/`endsOn`, no `status`. Those describe one delivery of an
 * exercise, not the exercise, and they are asked fresh every time a template is applied.
 *
 * [mediaPath] points into the `tugas-template` bucket, **not** `tugas-demo`. See migration `0006`
 * for why that separation is required rather than convenient.
 */
@Serializable
data class TaskTemplate(
    val id: String,
    @SerialName("relawan_id") val relawanId: String,
    val nama: String,
    val judul: String,
    val instruksi: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("priority_level") val priorityLevel: String,
    @SerialName("alat_bantu") val alatBantu: List<String>? = null,
    val recurrence: String = "once",
    @SerialName("recurrence_days") val recurrenceDays: List<Int>? = null,
    @SerialName("recurrence_day_of_month") val recurrenceDayOfMonth: Int? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** Display labels for the aids, with unknown stored values passed through untouched. */
    val alatBantuLabels: List<String> get() = TeachingAid.labelsOf(alatBantu)

    val recurrenceType: RecurrenceType get() = RecurrenceType.fromDb(recurrence)

    /** Days of week as [DayOfWeek], for prefilling the form. Invalid stored values are dropped. */
    val daysOfWeek: Set<DayOfWeek>
        get() = recurrenceDays.orEmpty()
            .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }
            .toSet()

    /**
     * Human-readable recurrence, e.g. "Setiap Senin, Kamis".
     *
     * A template carries no dates, so [RecurrenceRule.describe] is fed a throwaway anchor — it
     * only reads [RecurrenceRule.type], [RecurrenceRule.daysOfWeek], [RecurrenceRule.dayOfMonth]
     * and [RecurrenceRule.intervalDays], never the start date.
     */
    fun describeRecurrence(): String = RecurrenceRule(
        type = recurrenceType,
        startsOn = LocalDate.EPOCH,
        daysOfWeek = daysOfWeek,
        dayOfMonth = recurrenceDayOfMonth,
        intervalDays = recurrenceInterval,
    ).describe()
}

/** Insert payload for `public.tugas_template`. Omits id / created_at (DB defaults). */
@Serializable
data class TaskTemplateInsert(
    @SerialName("relawan_id") val relawanId: String,
    val nama: String,
    val judul: String,
    val instruksi: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    @SerialName("priority_level") val priorityLevel: String,
    @SerialName("alat_bantu") val alatBantu: List<String>? = null,
    val recurrence: String = "once",
    @SerialName("recurrence_days") val recurrenceDays: List<Int>? = null,
    @SerialName("recurrence_day_of_month") val recurrenceDayOfMonth: Int? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
) {
    companion object {
        /**
         * Turns an existing program into a template.
         *
         * This is where the split between "the exercise" and "this delivery of it" is enforced, so
         * it is a plain function over data rather than something buried in a ViewModel: the fields
         * it drops — [Task.anakId], [Task.deadline], [Task.startsOn], [Task.endsOn], [Task.status],
         * [Task.weekOf], [Task.stoppedAt] — are the whole design, and a test can hold them to it.
         *
         * [mediaPath] is the **already-copied** object in the `tugas-template` bucket, not
         * [Task.mediaPath]; passing the task's own path would store something no other volunteer
         * could read.
         */
        fun from(task: Task, nama: String, relawanId: String, mediaPath: String) = TaskTemplateInsert(
            relawanId = relawanId,
            nama = nama.trim(),
            judul = task.judul,
            instruksi = task.instruksi,
            mediaKind = task.mediaKind,
            mediaPath = mediaPath,
            priorityLevel = task.priorityLevel,
            alatBantu = task.alatBantu?.ifEmpty { null },
            recurrence = task.recurrence,
            recurrenceDays = task.recurrenceDays,
            recurrenceDayOfMonth = task.recurrenceDayOfMonth,
            recurrenceInterval = task.recurrenceInterval,
        )
    }
}
