package com.antoniusindra.sipatokkong.ui.relawan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.AnakOption
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import com.antoniusindra.sipatokkong.data.model.TugasInsert
import com.antoniusindra.sipatokkong.data.repository.AnakRepository
import com.antoniusindra.sipatokkong.data.repository.TugasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class CreateProgramAjarUiState(
    val children: List<AnakOption> = emptyList(),
    val isLoadingChildren: Boolean = true,
    val selectedAnakId: String? = null,
    val judul: String = "",
    val instruksi: String = "",
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val deadline: LocalDate? = null,
    val mediaUri: String? = null,
    val mediaName: String? = null,
    val mediaKind: MediaKind? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
) {
    val selectedChildName: String?
        get() = children.firstOrNull { it.id == selectedAnakId }?.nama
}

class CreateProgramAjarViewModel(
    private val anakRepository: AnakRepository,
    private val tugasRepository: TugasRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProgramAjarUiState())
    val uiState: StateFlow<CreateProgramAjarUiState> = _uiState.asStateFlow()

    // Store extension separately so the upload names the file correctly.
    private var mediaExt: String? = null

    init {
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChildren = true, error = null) }
            runCatching { anakRepository.childrenForCurrentRelawan() }
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            children = list,
                            isLoadingChildren = false,
                            selectedAnakId = it.selectedAnakId ?: list.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingChildren = false, error = e.message ?: "Gagal memuat daftar anak")
                    }
                }
        }
    }

    fun retryLoadChildren() = loadChildren()

    fun onAnakSelected(id: String) = _uiState.update { it.copy(selectedAnakId = id) }
    fun onJudulChange(v: String) = _uiState.update { it.copy(judul = v, error = null) }
    fun onInstruksiChange(v: String) = _uiState.update { it.copy(instruksi = v) }
    fun onPriorityChange(p: TaskPriority) = _uiState.update { it.copy(priority = p) }
    fun onDeadlineChange(d: LocalDate?) = _uiState.update { it.copy(deadline = d) }

    /** Resolve MIME of the picked media and map to a supported kind/extension. */
    fun onMediaPicked(uri: Uri) {
        val resolver = ServiceLocator.appContext.contentResolver
        val mime = resolver.getType(uri)
        val (kind, ext) = when (mime) {
            "image/jpeg" -> MediaKind.IMAGE to "jpg"
            "image/webp" -> MediaKind.IMAGE to "webp"
            "video/mp4" -> MediaKind.VIDEO to "mp4"
            else -> {
                _uiState.update { it.copy(error = "Format tidak didukung. Gunakan JPG, WebP, atau MP4.") }
                return
            }
        }
        mediaExt = ext
        _uiState.update {
            it.copy(
                mediaUri = uri.toString(),
                mediaName = "Media terpilih (.$ext)",
                mediaKind = kind,
                error = null,
            )
        }
    }

    fun submit() {
        val s = _uiState.value
        val relawanId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        when {
            s.selectedAnakId == null -> return fail("Pilih anak terlebih dahulu.")
            s.judul.isBlank() -> return fail("Judul program ajar wajib diisi.")
            s.mediaUri == null || s.mediaKind == null || mediaExt == null ->
                return fail("Foto/video demonstrasi wajib diunggah.")
            relawanId == null -> return fail("Sesi tidak valid. Silakan login ulang.")
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ServiceLocator.appContext.contentResolver
                        .openInputStream(Uri.parse(s.mediaUri))
                        ?.use { it.readBytes() }
                } ?: throw IllegalStateException("Gagal membaca file media.")

                val path = tugasRepository.uploadDemo(s.selectedAnakId!!, bytes, mediaExt!!)

                tugasRepository.create(
                    TugasInsert(
                        anakId = s.selectedAnakId,
                        relawanId = relawanId!!,
                        judul = s.judul.trim(),
                        instruksi = s.instruksi.ifBlank { null },
                        mediaKind = s.mediaKind!!.dbValue,
                        mediaPath = path,
                        priorityLevel = s.priority.dbValue,
                        deadline = s.deadline?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        weekOf = LocalDate.now()
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.message ?: "Gagal menyimpan program ajar") }
            }
        }
    }

    private fun fail(message: String) = _uiState.update { it.copy(error = message) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateProgramAjarViewModel(
                    anakRepository = ServiceLocator.anakRepository,
                    tugasRepository = ServiceLocator.tugasRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
