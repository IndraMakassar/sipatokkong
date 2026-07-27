package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the child responded during the exercise (stored inside the completion note). */
enum class ChildResponse(val label: String, val emoji: String) {
    SENANG("Senang & Fokus", "😊"),
    BIASA("Biasa Saja", "😐"),
    KEWALAHAN("Kewalahan", "😭"),
}

/** Insert payload for `public.dokumentasi` (parent's proof that the child did the task). */
@Serializable
data class DokumentasiInsert(
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

/** Insert payload for `public.tugas_penyelesaian` (marks a task done; a DB trigger sets status='done'). */
@Serializable
data class TugasPenyelesaianInsert(
    @SerialName("tugas_id") val tugasId: String,
    @SerialName("orangtua_id") val orangtuaId: String,
    @SerialName("dokumentasi_id") val dokumentasiId: String? = null,
    val catatan: String? = null,
)
