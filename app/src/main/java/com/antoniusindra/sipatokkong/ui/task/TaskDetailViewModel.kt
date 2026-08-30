package com.antoniusindra.sipatokkong.ui.task

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.media.MediaBucket
import com.antoniusindra.sipatokkong.core.media.MediaDownloadHandler
import com.antoniusindra.sipatokkong.core.media.MediaRef
import com.antoniusindra.sipatokkong.data.model.ChildResponse
import com.antoniusindra.sipatokkong.data.model.Documentation
import com.antoniusindra.sipatokkong.data.model.WorkDuration
import com.antoniusindra.sipatokkong.data.model.DocumentationInsert
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskCompletionInsert
import com.antoniusindra.sipatokkong.data.model.TaskOccurrences
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TaskDetailUiState(
    val isLoading: Boolean = true,
    val tugas: Task? = null,
    /** Which due date of the task is being worked on. */
    val occurrenceDate: LocalDate? = null,
    val mediaUrl: String? = null,
    val loadError: String? = null,
    /** True when this specific date has already been completed. */
    val alreadyDone: Boolean = false,
    /** The proof the parent already submitted for this date, once it has been read back. */
    val submittedProof: Documentation? = null,
    val submittedProofUrl: String? = null,
    // completion form
    val response: ChildResponse? = null,
    val duration: WorkDuration? = null,
    val catatan: String = "",
    val proofUri: String? = null,
    val proofKind: MediaKind? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val submitError: String? = null,
)

class TaskDetailViewModel(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    /** Saving media to the gallery, shared with every other screen that shows media. */
    val download = MediaDownloadHandler(viewModelScope)

    private var proofExt: String? = null
    private var loaded = false

    /** The volunteer's demo media, as a downloadable reference. */
    fun demoRef(): MediaRef? =
        _uiState.value.tugas?.let { MediaRef(MediaBucket.DEMO, it.mediaPath) }

    /** The parent's own submitted proof, as a downloadable reference. */
    fun proofRef(): MediaRef? =
        _uiState.value.submittedProof?.let { MediaRef(MediaBucket.DOKUMENTASI, it.mediaPath) }

    /**
     * @param occurrenceDate the day being worked on, or null when the caller has none.
     *
     * Null arrives from a notification tap: `notifications` has no date column, so the route
     * declares that argument optional. Resolving it here is free -- the recurrence rule needed to
     * answer it lives on the `Task` this function fetches on its first line anyway, so the
     * notification path costs exactly the same round trips as the ordinary one.
     */
    fun load(id: String, occurrenceDate: LocalDate?) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val tugas = taskRepository.getTask(id) ?: error("Tugas tidak ditemukan.")
                val date = occurrenceDate
                    ?: tugas.toRecurrenceRule().occurrenceNearest(LocalDate.now())
                    ?: error(NO_OCCURRENCE)
                val url = runCatching { taskRepository.signedDemoUrl(tugas.mediaPath) }.getOrNull()
                // Completion is per date, so a recurring program stays open on other days.
                val done = TaskOccurrences.isDone(
                    completions = taskRepository.completionsForCurrentUser(),
                    taskId = id,
                    date = date,
                )
                val proof = if (done) loadProof(id, date) else null
                Loaded(tugas, date, url, done, proof?.first, proof?.second)
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        tugas = loaded.tugas,
                        mediaUrl = loaded.mediaUrl,
                        // The resolved date, not the parameter: on the notification path the
                        // parameter is null and this is the only thing that knows the answer. The
                        // screen already prints it back ("Jadwal ..."), which is what tells a
                        // parent which day they landed on.
                        occurrenceDate = loaded.date,
                        alreadyDone = loaded.done,
                        submittedProof = loaded.proof,
                        submittedProofUrl = loaded.proofUrl,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "Gagal memuat tugas") }
            }
        }
    }

    /** Bundles what one load produced; a Triple stopped being readable at five values. */
    private data class Loaded(
        val tugas: Task,
        val date: LocalDate,
        val mediaUrl: String?,
        val done: Boolean,
        val proof: Documentation?,
        val proofUrl: String?,
    )

    /**
     * Reads back the proof the parent already sent for this date.
     *
     * Failure is swallowed: the proof is a bonus on an already-complete screen, and an error here
     * must not stop the task itself from opening. Older completions predate `dokumentasi_id` being
     * populated, so a null is normal rather than a fault.
     */
    private suspend fun loadProof(
        tugasId: String,
        occurrenceDate: LocalDate,
    ): Pair<Documentation, String?>? = runCatching {
        val completion = taskRepository.completionFor(tugasId, occurrenceDate.toString())
        val dokumentasiId = completion?.dokumentasiId ?: return@runCatching null
        val doc = taskRepository.getDocumentation(dokumentasiId) ?: return@runCatching null
        val url = runCatching { taskRepository.signedDocumentationUrl(doc.mediaPath) }.getOrNull()
        doc to url
    }.getOrNull()

    fun onResponseChange(r: ChildResponse) = _uiState.update { it.copy(response = r) }
    fun onDurationChange(d: WorkDuration) = _uiState.update { it.copy(duration = d) }
    fun onCatatanChange(v: String) = _uiState.update { it.copy(catatan = v) }

    fun onProofPicked(uri: Uri) {
        val mime = ServiceLocator.appContext.contentResolver.getType(uri)
        val (kind, ext) = when (mime) {
            "image/jpeg" -> MediaKind.IMAGE to "jpg"
            "image/webp" -> MediaKind.IMAGE to "webp"
            "video/mp4" -> MediaKind.VIDEO to "mp4"
            else -> {
                _uiState.update { it.copy(submitError = "Format tidak didukung. Gunakan JPG, WebP, atau MP4.") }
                return
            }
        }
        proofExt = ext
        _uiState.update { it.copy(proofUri = uri.toString(), proofKind = kind, submitError = null) }
    }

    fun submit() {
        val s = _uiState.value
        val tugas = s.tugas ?: return
        val occurrenceDate = s.occurrenceDate ?: return
        val orangtuaId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        if (s.proofUri == null || s.proofKind == null || proofExt == null) {
            _uiState.update { it.copy(submitError = "Pilih foto/video bukti terlebih dahulu.") }
            return
        }
        if (orangtuaId == null) {
            _uiState.update { it.copy(submitError = "Sesi tidak valid. Silakan login ulang.") }
            return
        }

        // Kemandirian and durasi are their own columns now, so the note holds only what the
        // parent actually typed. Previously the answer was prefixed into this text, which made it
        // impossible to count or chart.
        val note = s.catatan.trim().ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ServiceLocator.appContext.contentResolver
                        .openInputStream(Uri.parse(s.proofUri))
                        ?.use { it.readBytes() }
                } ?: throw IllegalStateException("Gagal membaca file bukti.")

                val path = taskRepository.uploadDocumentation(tugas.anakId, bytes, proofExt!!)

                val dokumentasiId = taskRepository.createDocumentation(
                    DocumentationInsert(
                        anakId = tugas.anakId,
                        orangtuaId = orangtuaId,
                        tugasId = tugas.id,
                        mediaKind = s.proofKind!!.dbValue,
                        mediaPath = path,
                        keterangan = note,
                    )
                )

                taskRepository.completeTask(
                    TaskCompletionInsert(
                        tugasId = tugas.id,
                        orangtuaId = orangtuaId,
                        occurrenceDate = occurrenceDate.toString(),
                        dokumentasiId = dokumentasiId,
                        catatan = note,
                        kemandirian = s.response?.dbValue,
                        durasi = s.duration?.dbValue,
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, submitError = e.message ?: "Gagal mengirim bukti") }
            }
        }
    }

    companion object {
        /**
         * Shown when a notification points at a task whose rule yields no occurrence at all.
         *
         * Reachable only from the dateless path, and phrased as a statement rather than a failure
         * because it is one: the programme really has nothing to open. The screen's error pane is
         * the right home for it -- there is no content behind it to preserve, so this is not
         * banner territory -- though retrying it will always produce the same answer.
         */
        const val NO_OCCURRENCE = "Tugas ini tidak punya jadwal yang bisa dibuka."

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TaskDetailViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
