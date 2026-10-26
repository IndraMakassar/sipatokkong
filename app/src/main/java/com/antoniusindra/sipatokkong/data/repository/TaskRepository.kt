package com.antoniusindra.sipatokkong.data.repository

import com.antoniusindra.sipatokkong.data.model.Documentation
import com.antoniusindra.sipatokkong.data.model.DocumentationInsert
import com.antoniusindra.sipatokkong.data.model.InsertedId
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskAidRow
import com.antoniusindra.sipatokkong.data.model.TaskCompletion
import com.antoniusindra.sipatokkong.data.model.TaskCompletionInsert
import com.antoniusindra.sipatokkong.data.model.TaskFeedbackUpdate
import com.antoniusindra.sipatokkong.data.model.TaskInsert
import com.antoniusindra.sipatokkong.data.model.TaskStopUpdate
import com.antoniusindra.sipatokkong.data.model.TaskSubmission
import com.antoniusindra.sipatokkong.data.model.TaskTemplate
import com.antoniusindra.sipatokkong.data.model.TaskTemplateInsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.*
import com.antoniusindra.sipatokkong.data.model.TaskPageRow
import kotlin.time.Duration.Companion.hours

class TaskRepository(private val client: SupabaseClient) {
    private val editJson = Json { encodeDefaults = true }

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
    suspend fun tasksForCurrentUser(): List<Task> {
        val rows = mutableListOf<Task>()
        do {
            val page = client.postgrest.from("tugas").select {
                order("created_at", Order.DESCENDING); order("id", Order.DESCENDING)
                range(rows.size.toLong(), rows.size.toLong() + 499)
            }.decodeList<Task>()
            rows.addAll(page)
        } while (page.size == 500)
        return rows
    }

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
    suspend fun completionsForCurrentUser(from: String? = null, to: String? = null): List<TaskCompletion> =
        client.postgrest.from("tugas_penyelesaian")
            .select(columns = Columns.list("tugas_id", "occurrence_date")) {
                filter { from?.let { gte("occurrence_date", it) }; to?.let { lte("occurrence_date", it) } }
            }
            .decodeList()

    /**
     * One completion, including the proof link and volunteer evaluation shown to the parent.
     *
     * Kept apart from [completionsForCurrentUser] on purpose: that one loads every completion for
     * the home screen, and carrying detail-only fields there would cost data on every entry to buy
     * something only the detail screen uses.
     */
    suspend fun completionFor(tugasId: String, occurrenceDate: String): TaskCompletion? =
        client.postgrest.from("tugas_penyelesaian")
            .select(columns = Columns.list("tugas_id", "occurrence_date", "dokumentasi_id", "feedback_relawan")) {
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

    /** Submissions/completions from parents for tasks belonging to the current volunteer. */
    suspend fun submissionsForCurrentVolunteer(): List<TaskSubmission> =
        client.postgrest.from("tugas_penyelesaian")
            .select(columns = Columns.raw("*, tugas(judul, anak_id, anak(nama))")) {
                order("completed_at", Order.DESCENDING)
            }
            .decodeList()

    /** Fetches a single submission by its id with joined task and child details. */
    suspend fun submissionFor(id: String): TaskSubmission? =
        client.postgrest.from("tugas_penyelesaian")
            .select(columns = Columns.raw("*, tugas(judul, anak_id, anak(nama))")) {
                filter { eq("id", id) }
            }
            .decodeSingleOrNull()

    /** Updates the volunteer feedback field for a task submission. */
    suspend fun updateFeedback(submissionId: String, feedback: String?) {
        client.postgrest.from("tugas_penyelesaian").update(TaskFeedbackUpdate(feedback = feedback)) {
            filter { eq("id", submissionId) }
        }
    }

    /** Programs created by the signed-in volunteer. RLS `tugas_select` already limits the rows. */
    suspend fun programsForCurrentVolunteer(): List<Task> = tasksForCurrentUser()

    /**
     * Stops a recurring program: no occurrences are generated from this day on.
     * Allowed by the existing `tugas_update` policy (`relawan_id = auth.uid()`).
     */
    suspend fun stopProgram(tugasId: String) {
        client.postgrest.from("tugas").update(TaskStopUpdate(stoppedAt = Instant.now().toString())) {
            filter { eq("id", tugasId) }
        }
    }

    // ---- Relawan: program ajar templates ----

    /**
     * Templates visible to the current user.
     *
     * Unfiltered on purpose, like [programsForCurrentVolunteer]: RLS policy `template_select`
     * admits any volunteer (templates are shared) and no parent, so the client adds nothing.
     */
    suspend fun templates(): List<TaskTemplate> =
        client.postgrest.from("tugas_template")
            .select { order("created_at", Order.DESCENDING) }
            .decodeList()

    suspend fun getTemplate(id: String): TaskTemplate? =
        client.postgrest.from("tugas_template")
            .select { filter { eq("id", id) } }
            .decodeSingleOrNull()

    suspend fun createTemplate(insert: TaskTemplateInsert): String =
        client.postgrest.from("tugas_template")
            .insert(insert) { select(Columns.list("id")) }
            .decodeSingle<InsertedId>()
            .id

    /** Allowed by `template_delete` only for the template's own author. */
    suspend fun deleteTemplate(id: String) {
        client.postgrest.from("tugas_template").delete { filter { eq("id", id) } }
    }

    /** Temporary signed URL for a template's demo object, for previewing it on the form. */
    suspend fun signedTemplateUrl(path: String): String =
        client.storage.from("tugas-template").createSignedUrl(path, 1.hours)

    /**
     * Copies a task's demo media into the template bucket; returns the new path.
     *
     * Server-side — the file never travels through the phone a second time, which matters because
     * nothing in this app compresses media and the bucket ceiling is 50 MB.
     */
    suspend fun copyDemoToTemplate(fromPath: String, relawanId: String): String =
        copyBetween("tugas-demo", "tugas-template", fromPath, relawanId)

    /**
     * Copies a template's media back into `tugas-demo` under [anakId]; returns the new path.
     *
     * Required, not an optimisation: `tugasdemo_read` grants access by the child id in the first
     * path segment, so only after this copy can that child's **parent** open the demo at all.
     */
    suspend fun copyTemplateToDemo(fromPath: String, anakId: String): String =
        copyBetween("tugas-template", "tugas-demo", fromPath, anakId)

    suspend fun historyPage(from: String?, to: String?, cursor: TaskPageRow?): List<TaskPageRow> =
        client.postgrest.rpc("parent_task_history", buildJsonObject {
            from?.let { put("p_from", it) }; to?.let { put("p_to", it) }
            cursor?.let { put("p_cursor_date", it.occurrenceDate); put("p_cursor_id", it.id) }
        }).decodeList()

    suspend fun oldOverduePage(before: String, cursor: TaskPageRow?): List<TaskPageRow> =
        client.postgrest.rpc("parent_old_overdue", buildJsonObject {
            put("p_before", before)
            cursor?.let { put("p_cursor_date", it.occurrenceDate); put("p_cursor_id", it.id) }
        }).decodeList()

    suspend fun editProgram(task: Task, requestId: String, changes: TaskInsert) {
        client.postgrest.rpc("edit_program", buildJsonObject {
            put("p_task_id", task.id); put("p_revision", task.revision); put("p_request_id", requestId)
            put("p_changes", editJson.encodeToJsonElement(TaskInsert.serializer(), changes))
        })
    }

    suspend fun cancelOneOff(task: Task) {
        client.postgrest.rpc("cancel_one_off", buildJsonObject {
            put("p_task_id", task.id); put("p_revision", task.revision)
        })
    }

    // ---- shared ----

    private suspend fun uploadTo(bucket: String, anakId: String, bytes: ByteArray, ext: String): String {
        val path = mediaPath(anakId, ext)
        client.storage.from(bucket).upload(path, bytes) { upsert = false }
        return path
    }

    private suspend fun copyBetween(
        fromBucket: String,
        toBucket: String,
        fromPath: String,
        ownerId: String,
    ): String {
        // Both buckets only accept jpg/webp/mp4, so a stored path always has an extension.
        val path = mediaPath(ownerId, fromPath.substringAfterLast('.'))
        client.storage.from(fromBucket).copy(fromPath, path, destinationBucket = toBucket)
        return path
    }

    /**
     * `{ownerId}/{yyyy}/{MM}/{uuid}.{ext}` — the one path convention, used by every write.
     *
     * The first segment is load-bearing: storage RLS reads it back with `storage_child_id` in the
     * per-child buckets and `storage_owner_id` in `tugas-template`. Whether it means a child or a
     * volunteer depends on the bucket, which is why the parameter is named neutrally.
     */
    private fun mediaPath(ownerId: String, ext: String): String {
        val now = LocalDate.now()
        val month = now.monthValue.toString().padStart(2, '0')
        return "$ownerId/${now.year}/$month/${UUID.randomUUID()}.$ext"
    }
}
