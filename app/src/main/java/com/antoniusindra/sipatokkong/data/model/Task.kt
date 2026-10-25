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
        /**
         * Cap on a volunteer-typed aid. The longest built-in label, "Buku cerita bergambar", is 21
         * characters, so this leaves room without letting a stray paste fill the column.
         */
        const val MAX_CUSTOM_LENGTH = 40

        private val WHITESPACE = Regex("\\s+")

        fun fromDb(value: String): TeachingAid? = entries.firstOrNull { it.dbValue == value }

        /**
         * Stored values turned into display labels.
         *
         * An unrecognised value is passed through as-is rather than dropped. That covers two cases
         * at once: a list that changed between releases, and an aid the volunteer typed themselves
         * — the latter is stored as plain text and is *meant* to render verbatim.
         */
        fun labelsOf(values: List<String>?): List<String> = values.orEmpty().map(::labelOf)

        /** One stored value as it should read on screen. See [labelsOf] for why unknowns pass through. */
        fun labelOf(value: String): String = fromDb(value)?.label ?: value

        /**
         * A volunteer-typed aid cleaned up for storage: trimmed, runs of inner whitespace collapsed
         * to one space, and capped at [MAX_CUSTOM_LENGTH]. Returns null when nothing survives.
         */
        fun normalizeCustom(raw: String): String? =
            raw.trim()
                .replace(WHITESPACE, " ")
                .take(MAX_CUSTOM_LENGTH)
                .trim()
                .ifBlank { null }

        /**
         * Whatever the volunteer typed, turned into the value to store.
         *
         * A typed name that already exists resolves back to its [dbValue] rather than becoming a
         * near-duplicate: "Cermin", "CERMIN" and "cermin" all end up as `cermin`. This matching is
         * the only thing standing between a free-text field and a fragmented aid list, since
         * nothing in the database constrains the column.
         */
        fun resolve(raw: String): String? {
            val clean = normalizeCustom(raw) ?: return null
            return matchBuiltIn(clean)?.dbValue ?: clean
        }

        /** The built-in an arbitrary string names, by either label or stored value, ignoring case. */
        fun matchBuiltIn(value: String): TeachingAid? = entries.firstOrNull {
            it.dbValue.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true)
        }

        /**
         * Selected values in a stable order: built-ins in declaration order, then custom entries
         * alphabetically.
         *
         * Deliberately not tap order — the parent should see the same list arranged the same way
         * every time, however the volunteer happened to pick it.
         */
        fun sortedForStorage(values: Set<String>): List<String> {
            val builtIn = entries.filter { it.dbValue in values }.map { it.dbValue }
            val custom = values.filter { matchBuiltIn(it) == null }.sortedBy { it.lowercase() }
            return builtIn + custom
        }

        /**
         * Aids used on past tasks turned into menu suggestions.
         *
         * Built-ins are dropped — they are always in the menu anyway — and duplicates are removed
         * ignoring case, so one volunteer's "Gunting" and "gunting" collapse into a single entry
         * instead of offering the same aid twice.
         */
        fun suggestionsFrom(stored: List<String>): List<String> =
            stored.mapNotNull { normalizeCustom(it) }
                .filter { matchBuiltIn(it) == null }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
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
    // Serialised OUT of the request whenever it equals this default -- kotlinx.serialization omits
    // defaulted properties, and supabase-kt leaves `encodeDefaults` off. That is harmless here and
    // only here, because the column is `not null default 'once'` too, so the value the server
    // applies is the value that was dropped. It stops being harmless the moment those two defaults
    // disagree, and nothing enforces that they agree. See NotificationReadUpdate, where the same
    // omission silently broke every write because there was no column default to catch it.
    val recurrence: String = "once",
    @SerialName("recurrence_days") val recurrenceDays: List<Int>? = null,
    @SerialName("recurrence_day_of_month") val recurrenceDayOfMonth: Int? = null,
    @SerialName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerialName("starts_on") val startsOn: String? = null,
    @SerialName("ends_on") val endsOn: String? = null,
    /** [TeachingAid.dbValue] entries; null or empty when the task needs no aids. */
    @SerialName("alat_bantu") val alatBantu: List<String>? = null,
)

/**
 * Narrow read model for gathering aid suggestions — only `alat_bantu` is selected, so the query
 * stays cheap enough to run every time the create-task screen opens.
 */
@Serializable
data class TaskAidRow(
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
    @SerialName("cancelled_at") val cancelledAt: String? = null,
    @SerialName("previous_version_id") val previousVersionId: String? = null,
    val revision: Int = 0,
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
