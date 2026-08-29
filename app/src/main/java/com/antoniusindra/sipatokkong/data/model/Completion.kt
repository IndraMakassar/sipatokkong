package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How independently the child managed the exercise — mapped to the Postgres enum
 * `tingkat_kemandirian`. Declared best-to-least independent; that order drives the on-screen grid.
 *
 * Emoji are kept because the target users are low-literacy parents: a picture reads faster than a
 * phrase. "Belum Bisa" uses a seedling rather than a cross — this is a therapy tool, not a report
 * card, and a child who cannot do something yet is still growing.
 */
enum class ChildResponse(val dbValue: String, val label: String, val emoji: String) {
    MANDIRI("mandiri", "Mandiri", "💪"),
    DENGAN_BANTUAN("dengan_bantuan", "Dengan Bantuan", "🤝"),
    BANTUAN_PENUH("bantuan_penuh", "Dengan Bantuan Penuh", "🤲"),
    BELUM_BISA("belum_bisa", "Belum Bisa", "🌱"),
}

/**
 * Roughly how long the child worked, mapped to the Postgres enum `durasi_pengerjaan`.
 *
 * Buckets rather than a minute field: one tap instead of estimating and typing a number, which
 * matters for this audience and still shows a trend over time.
 */
enum class WorkDuration(val dbValue: String, val label: String) {
    UNDER_5("kurang_5", "< 5 menit"),
    FROM_5_TO_15("5_15", "5–15 menit"),
    FROM_15_TO_30("15_30", "15–30 menit"),
    OVER_30("lebih_30", "> 30 menit"),
}

/** Insert payload for `public.dokumentasi` (parent's proof that the child did the task). */
@Serializable
data class DocumentationInsert(
    @SerialName("anak_id") val anakId: String,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("tugas_id") val tugasId: String? = null,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    val keterangan: String? = null,
)

/** Minimal projection to read back a freshly-inserted row id. */
@Serializable
data class InsertedId(val id: String)

/**
 * Insert payload for `public.tugas_penyelesaian` — marks one occurrence of a task done.
 *
 * `occurrence_date` is what makes recurring programs work: the unique constraint is
 * `(tugas_id, occurrence_date)`, so the same program can be completed once per due date.
 * A DB trigger still flips `status` to 'done', but only for one-shot tasks.
 */
@Serializable
data class TaskCompletionInsert(
    @SerialName("tugas_id") val tugasId: String,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("occurrence_date") val occurrenceDate: String,
    @SerialName("dokumentasi_id") val dokumentasiId: String? = null,
    val catatan: String? = null,
    /** [ChildResponse.dbValue]; null when the parent skipped the question. */
    val kemandirian: String? = null,
    /** [WorkDuration.dbValue]; null when the parent skipped the question. */
    val durasi: String? = null,
)

/**
 * Minimal read model: which occurrences of which tasks are already done.
 *
 * [dokumentasiId] is only populated by the single-occurrence query on the detail screen. The home
 * list deliberately keeps selecting the two narrow columns — it fetches every completion the
 * parent has, and widening that payload costs data on every home-screen entry.
 */
@Serializable
data class TaskCompletion(
    @SerialName("tugas_id") val tugasId: String,
    @SerialName("occurrence_date") val occurrenceDate: String,
    @SerialName("dokumentasi_id") val dokumentasiId: String? = null,
)

/**
 * Full `public.dokumentasi` row — the proof media a parent submitted.
 *
 * Until now this table was write-only: the app inserted rows and never read one back, so the
 * parent's own evidence disappeared from the UI the moment it was uploaded.
 */
@Serializable
data class Documentation(
    val id: String,
    @SerialName("anak_id") val anakId: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("media_path") val mediaPath: String,
    val keterangan: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
