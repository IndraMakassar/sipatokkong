package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Insert payload for `public.presensi` — one home visit logged by a volunteer.
 *
 * `fotoPath` is non-null here even though the column is nullable: the proof photo is required by
 * the form, and letting the DTO express "no photo" would make it possible to write a visit with no
 * evidence by accident. Rows created before this feature existed can still be read back as null —
 * see [Presensi].
 */
@Serializable
data class PresensiInsert(
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
    /** ISO `yyyy-MM-dd`, matching the `date` column. */
    val tanggal: String,
    val catatan: String? = null,
    @SerialName("foto_path") val fotoPath: String,
)

/**
 * Full `public.presensi` row.
 *
 * `fotoThumbPath` is read but never written — nothing in this app generates thumbnails yet, the
 * same situation as [Task.mediaThumbPath]. It is mapped so the column is not silently lost if
 * something starts filling it server-side.
 */
@Serializable
data class Presensi(
    val id: String,
    @SerialName("anak_id") val anakId: String,
    @SerialName("relawan_id") val relawanId: String,
    val tanggal: String,
    val catatan: String? = null,
    @SerialName("foto_path") val fotoPath: String? = null,
    @SerialName("foto_thumb_path") val fotoThumbPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** Null when the stored text is not a valid date, rather than throwing while a list renders. */
    val parsedDate: LocalDate? get() = runCatching { LocalDate.parse(tanggal) }.getOrNull()
}
