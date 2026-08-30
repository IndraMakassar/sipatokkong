package com.antoniusindra.sipatokkong.ui.volunteer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.core.media.ImageCompressor
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.PresensiInsert
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.PresensiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.antoniusindra.sipatokkong.core.net.Connectivity

data class CreatePresensiUiState(
    val children: List<ChildOption> = emptyList(),
    val isLoadingChildren: Boolean = true,
    val selectedAnakId: String? = null,
    /** Defaults to today: a visit is nearly always logged on the day it happened. */
    val tanggal: LocalDate = LocalDate.now(),
    val catatan: String = "",
    val fotoUri: String? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
) {
    val selectedChildName: String?
        get() = children.firstOrNull { it.id == selectedAnakId }?.nama

    /**
     * Derived here rather than in the composable so the rule is unit-testable: the suite has no
     * coroutine or mocking libraries, so anything living inside `submit()` cannot be covered.
     */
    val canSubmit: Boolean
        get() = selectedAnakId != null && fotoUri != null && !isSubmitting
}

/**
 * The volunteer's "catat kunjungan" form.
 *
 * Unlike the list ViewModels, this one loads in `init`: a create form is always entered fresh, so
 * there is no stale state for a screen-driven `LaunchedEffect` to fix — the same split
 * `CreateTaskViewModel` makes.
 */
class CreatePresensiViewModel(
    private val childRepository: ChildRepository,
    private val presensiRepository: PresensiRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePresensiUiState())
    val uiState: StateFlow<CreatePresensiUiState> = _uiState.asStateFlow()

    init {
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChildren = true, error = null) }
            runCatching { childRepository.childrenForCurrentVolunteer() }
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
                        it.copy(isLoadingChildren = false, error = ErrorText.of(e, "Gagal memuat daftar anak"))
                    }
                }
        }
    }

    fun retryLoadChildren() = loadChildren()

    fun onAnakSelected(id: String) = _uiState.update { it.copy(selectedAnakId = id, error = null) }
    fun onTanggalChange(d: LocalDate?) = _uiState.update {
        it.copy(tanggal = d ?: it.tanggal, error = null)
    }
    fun onCatatanChange(v: String) = _uiState.update { it.copy(catatan = v) }

    /**
     * Accepts any image the picker returns — unlike the task form there is no MIME whitelist here,
     * because everything is re-encoded to JPEG before upload anyway.
     */
    fun onFotoPicked(uri: Uri) = _uiState.update {
        it.copy(fotoUri = uri.toString(), error = null)
    }

    fun submit() {
        val s = _uiState.value
        val relawanId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        when {
            s.selectedAnakId == null -> return fail("Pilih anak terlebih dahulu.")
            s.fotoUri == null -> return fail("Foto bukti kunjungan wajib diunggah.")
            s.tanggal.isAfter(LocalDate.now()) -> return fail("Tanggal kunjungan tidak boleh di masa depan.")
            relawanId == null -> return fail("Sesi tidak valid. Silakan login ulang.")
        }

        // Asked before the attempt, not after it. Without this the user waits out a connection
        // timeout to be told something the phone already knew -- and here they burn upload data
        // getting there. A false positive is possible (see Connectivity), so the mapped error on
        // the way back still has the last word.
        if (!Connectivity.isOnline(ServiceLocator.appContext)) {
            return fail(ErrorText.OFFLINE)
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                val bytes = ImageCompressor.compressToJpeg(
                    ServiceLocator.appContext,
                    Uri.parse(s.fotoUri),
                )
                // Last guard before the bucket's own 6 MB limit rejects it with a raw error.
                if (bytes.size > ImageCompressor.MAX_UPLOAD_BYTES) {
                    throw IllegalStateException("Foto terlalu besar untuk diunggah. Coba foto lain.")
                }

                val path = presensiRepository.uploadFoto(s.selectedAnakId!!, bytes)

                presensiRepository.create(
                    PresensiInsert(
                        anakId = s.selectedAnakId,
                        relawanId = relawanId!!,
                        tanggal = s.tanggal.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        catatan = s.catatan.trim().ifBlank { null },
                        fotoPath = path,
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = ErrorText.of(e, "Gagal menyimpan presensi")) }
            }
        }
    }

    /**
     * Turns the unique-index violation into something a volunteer can act on.
     *
     * `presensi_unik_harian` raises Postgres 23505, which arrives as a Postgrest error mentioning
     * the index name — unreadable, and it looks like a crash rather than "you already did this".
     */
    private fun fail(message: String) = _uiState.update { it.copy(error = message) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreatePresensiViewModel(
                    childRepository = ServiceLocator.childRepository,
                    presensiRepository = ServiceLocator.presensiRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
