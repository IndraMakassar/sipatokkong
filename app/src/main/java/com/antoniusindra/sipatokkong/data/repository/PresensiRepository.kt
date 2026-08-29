package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.data.model.PresensiInsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.hours

/** Volunteer attendance ("presensi") — one row per home visit, with a proof photo. */
class PresensiRepository(private val client: SupabaseClient) {

    /**
     * Uploads the proof photo to the private `presensi` bucket and returns the stored path.
     *
     * Always `.jpg`: the bucket only accepts `image/jpeg` and `image/webp`, and everything is
     * re-encoded to JPEG on the way in to stay under its 6 MB cap.
     */
    suspend fun uploadFoto(anakId: String, bytes: ByteArray): String =
        client.uploadChildMedia("presensi", anakId, bytes, "jpg")

    /**
     * Logs one visit. RLS `presensi_insert` requires `relawan_id = auth.uid()` **and**
     * `is_relawan_of(anak_id)`, so a volunteer cannot write for a child they are not assigned to.
     *
     * The unique index `presensi_unik_harian` rejects a second row for the same
     * (relawan, anak, tanggal); callers are expected to translate that into a readable message.
     */
    suspend fun create(insert: PresensiInsert) {
        client.postgrest.from("presensi").insert(insert)
    }

    /**
     * The signed-in volunteer's own visits, newest first.
     *
     * No explicit filter: RLS `presensi_select` already narrows this to `relawan_id = auth.uid()`
     * for a volunteer, the same way `ChildRepository.childrenForCurrentVolunteer()` leans on
     * `anak_select`.
     */
    suspend fun listForCurrentVolunteer(): List<Presensi> =
        client.postgrest.from("presensi")
            .select { order("tanggal", Order.DESCENDING) }
            .decodeList()

    /**
     * Every visit in a date range, both ends inclusive — for the admin recap.
     * The same `presensi_select` policy hands an admin all rows via `is_admin()`.
     */
    suspend fun listBetween(from: LocalDate, to: LocalDate): List<Presensi> =
        client.postgrest.from("presensi")
            .select {
                filter {
                    gte("tanggal", from.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    lte("tanggal", to.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
                order("tanggal", Order.DESCENDING)
            }
            .decodeList()

    /** Temporary signed URL for a private proof photo (for display). */
    suspend fun signedFotoUrl(path: String): String =
        client.storage.from("presensi").createSignedUrl(path, 1.hours)
}
