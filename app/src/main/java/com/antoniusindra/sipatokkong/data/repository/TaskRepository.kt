package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.DocumentationInsert
import com.antoniusindra.sipatokkong.data.model.InsertedId
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskInsert
import com.antoniusindra.sipatokkong.data.model.TaskCompletion
import com.antoniusindra.sipatokkong.data.model.TaskCompletionInsert
import com.antoniusindra.sipatokkong.data.model.TaskStopUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class TaskRepository(private val client: SupabaseClient) {

    // ---- Relawan: create a tugas (with demo media) ----

    /**
     * Uploads the demo photo/video to the private `tugas-demo` bucket and returns the stored path.
     * Path convention: `{anakId}/{yyyy}/{MM}/{uuid}.{ext}` (child id is the first folder segment).
     */
    suspend fun uploadDemo(anakId: String, bytes: ByteArray, ext: String): String =
        uploadTo("tugas-demo", anakId, bytes, ext)

    suspend fun create(insert: TaskInsert) {
        client.postgrest.from("tugas").insert(insert)
    }

    // ---- Orang tua: read tasks + complete them ----

    /** Tasks visible to the current user. RLS scopes this to the parent's child(ren). */
    suspend fun tasksForCurrentUser(): List<Task> =
        client.postgrest.from("tugas")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList()

    suspend fun getTask(id: String): Task? =
        client.postgrest.from("tugas")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    /** Temporary signed URL for a private demo-media object (for display). */
    suspend fun signedDemoUrl(path: String): String =
        client.storage.from("tugas-demo").createSignedUrl(path, 1.hours)

    /** Uploads the parent's proof media to the private `dokumentasi` bucket; returns the path. */
    suspend fun uploadDocumentation(anakId: String, bytes: ByteArray, ext: String): String =
        uploadTo("dokumentasi", anakId, bytes, ext)

    /** Inserts a `dokumentasi` row and returns its id. */
    suspend fun createDocumentation(insert: DocumentationInsert): String =
        client.postgrest.from("dokumentasi")
            .insert(insert) { select(Columns.list("id")) }
            .decodeSingle<InsertedId>()
            .id

    /**
     * Marks one occurrence done by inserting into `tugas_penyelesaian`.
     * The unique constraint `(tugas_id, occurrence_date)` prevents double-completing a date.
     */
    suspend fun completeTask(insert: TaskCompletionInsert) {
        client.postgrest.from("tugas_penyelesaian").insert(insert)
    }

    /**
     * Which occurrences are already done, for the tasks the current user can see.
     * RLS `penyelesaian_select` scopes this to the parent's own rows (or the volunteer's tasks).
     */
    suspend fun completionsForCurrentUser(): List<TaskCompletion> =
        client.postgrest.from("tugas_penyelesaian")
            .select(columns = Columns.list("tugas_id", "occurrence_date"))
            .decodeList()

    // ---- Relawan: manage own programs ----

    /** Programs created by the signed-in volunteer. RLS `tugas_select` already limits the rows. */
    suspend fun programsForCurrentVolunteer(): List<Task> =
        client.postgrest.from("tugas")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList()

    /**
     * Stops a recurring program: no occurrences are generated from this day on.
     * Allowed by the existing `tugas_update` policy (`relawan_id = auth.uid()`).
     */
    suspend fun stopProgram(tugasId: String) {
        client.postgrest.from("tugas").update(TaskStopUpdate(stoppedAt = Instant.now().toString())) {
            filter { eq("id", tugasId) }
        }
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
