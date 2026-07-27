package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.DokumentasiInsert
import com.antoniusindra.sipatokkong.data.model.InsertedId
import com.antoniusindra.sipatokkong.data.model.Tugas
import com.antoniusindra.sipatokkong.data.model.TugasInsert
import com.antoniusindra.sipatokkong.data.model.TugasPenyelesaianInsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class TugasRepository(private val client: SupabaseClient) {

    // ---- Relawan: create a tugas (with demo media) ----

    /**
     * Uploads the demo photo/video to the private `tugas-demo` bucket and returns the stored path.
     * Path convention: `{anakId}/{yyyy}/{MM}/{uuid}.{ext}` (child id is the first folder segment).
     */
    suspend fun uploadDemo(anakId: String, bytes: ByteArray, ext: String): String =
        uploadTo("tugas-demo", anakId, bytes, ext)

    suspend fun create(insert: TugasInsert) {
        client.postgrest.from("tugas").insert(insert)
    }

    // ---- Orang tua: read tasks + complete them ----

    /** Tasks visible to the current user. RLS scopes this to the parent's child(ren). */
    suspend fun tasksForCurrentUser(): List<Tugas> =
        client.postgrest.from("tugas")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList()

    suspend fun getTugas(id: String): Tugas? =
        client.postgrest.from("tugas")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    /** Temporary signed URL for a private demo-media object (for display). */
    suspend fun signedDemoUrl(path: String): String =
        client.storage.from("tugas-demo").createSignedUrl(path, 1.hours)

    /** Uploads the parent's proof media to the private `dokumentasi` bucket; returns the path. */
    suspend fun uploadDokumentasi(anakId: String, bytes: ByteArray, ext: String): String =
        uploadTo("dokumentasi", anakId, bytes, ext)

    /** Inserts a `dokumentasi` row and returns its id. */
    suspend fun createDokumentasi(insert: DokumentasiInsert): String =
        client.postgrest.from("dokumentasi")
            .insert(insert) { select(Columns.list("id")) }
            .decodeSingle<InsertedId>()
            .id

    /** Marks a task done by inserting into `tugas_penyelesaian` (trigger flips tugas.status). */
    suspend fun completeTugas(insert: TugasPenyelesaianInsert) {
        client.postgrest.from("tugas_penyelesaian").insert(insert)
    }

    // ---- shared ----

    private suspend fun uploadTo(bucket: String, anakId: String, bytes: ByteArray, ext: String): String {
        val now = LocalDate.now()
        val month = now.monthValue.toString().padStart(2, '0')
        val path = "$anakId/${now.year}/$month/${UUID.randomUUID()}.$ext"
        client.storage.from(bucket).upload(path, bytes) { upsert = false }
        return path
    }
}
