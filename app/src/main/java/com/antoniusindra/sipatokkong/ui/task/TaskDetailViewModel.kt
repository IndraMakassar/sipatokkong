package com.antoniusindra.sipatokkong.ui.task

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.ChildResponse
import com.antoniusindra.sipatokkong.data.model.DocumentationInsert
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskCompletionInsert
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
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
    val mediaUrl: String? = null,
    val loadError: String? = null,
    // completion form
    val response: ChildResponse? = null,
    val catatan: String = "",
    val proofUri: String? = null,
    val proofKind: MediaKind? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val submitError: String? = null,
) {
    val alreadyDone: Boolean get() = tugas?.status == "done"
}

class TaskDetailViewModel(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskDetailUiState())
    val uiState: StateFlow<TaskDetailUiState> = _uiState.asStateFlow()

    private var proofExt: String? = null
    private var loaded = false

    fun load(id: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val tugas = taskRepository.getTask(id) ?: error("Tugas tidak ditemukan.")
                val url = runCatching { taskRepository.signedDemoUrl(tugas.mediaPath) }.getOrNull()
                tugas to url
            }.onSuccess { (tugas, url) ->
                _uiState.update { it.copy(isLoading = false, tugas = tugas, mediaUrl = url) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "Gagal memuat tugas") }
            }
        }
    }

    fun onResponseChange(r: ChildResponse) = _uiState.update { it.copy(response = r) }
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
        val orangtuaId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        if (s.proofUri == null || s.proofKind == null || proofExt == null) {
            _uiState.update { it.copy(submitError = "Pilih foto/video bukti terlebih dahulu.") }
            return
        }
        if (orangtuaId == null) {
            _uiState.update { it.copy(submitError = "Sesi tidak valid. Silakan login ulang.") }
            return
        }

        val note = buildString {
            s.response?.let { append("Respon anak: ${it.label}.") }
            if (s.catatan.isNotBlank()) {
                if (isNotEmpty()) append(" ")
                append(s.catatan.trim())
            }
        }.ifBlank { null }

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
                        dokumentasiId = dokumentasiId,
                        catatan = note,
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, submitError = e.message ?: "Gagal mengirim bukti") }
            }
        }
    }

    companion object {
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
