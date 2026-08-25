package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Documentation
import com.antoniusindra.sipatokkong.data.model.DocumentationInsert
import com.antoniusindra.sipatokkong.data.model.InsertedId
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskAidRow
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

    /**
     * Aids this volunteer has used before, for suggesting in the create-task menu.
     *
     * RLS `tugas_select` allows a volunteer only their own rows (`relawan_id = auth.uid()`), so
     * this is their own history — aids do not travel between volunteers.
     */
    suspend fun aidsUsedByCurrentVolunteer(): List<String> =
        client.postgrest.from("tugas")
            .select(columns = Columns.list("alat_bantu"))
            .decodeList<TaskAidRow>()
            .flatMap { it.alatBantu.orEmpty() }

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

    /**
     * Temporary signed URL for a parent's proof media (for display).
     * RLS `dokumentasi_read` allows the parent, the assigned volunteer, and admins.
     */
    suspend fun signedDocumentationUrl(path: String): String =
        client.storage.from("dokumentasi").createSignedUrl(path, 1.hours)

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

    /**
     * One completion, including the `dokumentasi_id` that links to the proof the parent sent.
     *
     * Kept apart from [completionsForCurrentUser] on purpose: that one loads every completion for
     * the home screen, and carrying a third column there would cost data on every entry to buy
     * something only the detail screen uses.
     */
    suspend fun completionFor(tugasId: String, occurrenceDate: String): TaskCompletion? =
        client.postgrest.from("tugas_penyelesaian")
            .select(columns = Columns.list("tugas_id", "occurrence_date", "dokumentasi_id")) {
                filter {
                    eq("tugas_id", tugasId)
                    eq("occurrence_date", occurrenceDate)
                }
            }
            .decodeSingleOrNull()

    /** One `dokumentasi` row by id — the proof attached to a completion. */
    suspend fun getDocumentation(id: String): Documentation? =
        client.postgrest.from("dokumentasi")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

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

    private suspend fun uploadTo(bucket: String, anakId: String, bytes: ByteArray, ext: String): String =
        client.uploadChildMedia(bucket, anakId, bytes, ext)
}
