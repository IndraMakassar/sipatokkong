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

/**
 * Household aids a task may involve, stored in `tugas.alat_bantu` as an array of [dbValue].
 *
 * The list is deliberately made of cheap or already-owned items: the families this app serves are
 * low-income, so proposing clinical equipment would put the homework out of reach. Each entry
 * traces back to therapy the app already describes — fabric textures and vowel practice appear in
 * its own example tasks, and "starting to hold a spoon" is the README's example of progress.
 */
enum class TeachingAid(val dbValue: String, val label: String) {
    KAIN("kain_tekstur", "Kain aneka tekstur"),
    CERMIN("cermin", "Cermin"),
    SEDOTAN("sedotan", "Sedotan"),
    SENDOK_PIRING("sendok_piring", "Sendok & piring"),
    KARTU_GAMBAR("kartu_gambar", "Kartu bergambar"),
    BUKU_CERITA("buku_cerita", "Buku cerita bergambar"),
    BOLA("bola", "Bola"),
    BALOK_PUZZLE("balok_puzzle", "Balok susun / puzzle"),
    KRAYON("krayon", "Krayon / pensil warna"),
    BERAS_BIJI("beras_biji", "Beras / biji-bijian"),
    BOTOL_TUTUP("botol_tutup", "Botol & tutup"),
    MAINAN_BUNYI("mainan_bunyi", "Mainan bunyi");

    companion object {
        fun fromDb(value: String): TeachingAid? = entries.firstOrNull { it.dbValue == value }

        /**
         * Stored values turned into display labels.
         *
         * An unrecognised value is passed through as-is rather than dropped. If this list ever
         * changes, tasks created under the old list keep showing their aid instead of silently
         * losing it.
         */
        fun labelsOf(values: List<String>?): List<String> =
            values.orEmpty().map { fromDb(it)?.label ?: it }
    }
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
    /** [TeachingAid.dbValue] entries; null or empty when the task needs no aids. */
    @SerialName("alat_bantu") val alatBantu: List<String>? = null,
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
    @SerialName("alat_bantu") val alatBantu: List<String>? = null,
) {
    /** Display labels for the aids, with unknown stored values passed through untouched. */
    val alatBantuLabels: List<String> get() = TeachingAid.labelsOf(alatBantu)

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
