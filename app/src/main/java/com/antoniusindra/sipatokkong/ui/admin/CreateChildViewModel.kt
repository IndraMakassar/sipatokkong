package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.ChildInsert
import com.antoniusindra.sipatokkong.data.model.ProfileOption
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class CreateChildUiState(
    val parents: List<ProfileOption> = emptyList(),
    val relawans: List<ProfileOption> = emptyList(),
    val isLoadingOptions: Boolean = true,
    val selectedOrangtuaId: String? = null,
    val nama: String = "",
    val tanggalLahir: LocalDate? = null,
    val catatan: String = "",
    val selectedRelawanId: String? = null,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
) {
    val selectedParentName: String? get() = parents.firstOrNull { it.id == selectedOrangtuaId }?.fullName
    val selectedRelawanName: String? get() = relawans.firstOrNull { it.id == selectedRelawanId }?.fullName
}

class CreateChildViewModel(
    private val childRepository: ChildRepository,
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateChildUiState())
    val uiState: StateFlow<CreateChildUiState> = _uiState.asStateFlow()

    init {
        loadOptions()
    }

    private fun loadOptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOptions = true, error = null) }
            runCatching {
                val parents = profileRepository.profilesByRole("orangtua")
                val relawans = profileRepository.profilesByRole("relawan")
                parents to relawans
            }.onSuccess { (parents, relawans) ->
                _uiState.update {
                    it.copy(parents = parents, relawans = relawans, isLoadingOptions = false)
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoadingOptions = false, error = ErrorText.of(e, "Gagal memuat data")) }
            }
        }
    }

    fun onOrangtuaSelected(id: String) = _uiState.update { it.copy(selectedOrangtuaId = id, error = null) }
    fun onNamaChange(v: String) = _uiState.update { it.copy(nama = v, error = null) }
    fun onTanggalLahirChange(d: LocalDate?) = _uiState.update { it.copy(tanggalLahir = d) }
    fun onCatatanChange(v: String) = _uiState.update { it.copy(catatan = v) }
    fun onRelawanSelected(id: String?) = _uiState.update { it.copy(selectedRelawanId = id) }

    fun submit() {
        val s = _uiState.value
        if (s.selectedOrangtuaId == null) {
            _uiState.update { it.copy(error = "Pilih orang tua terlebih dahulu.") }
            return
        }
        if (s.nama.isBlank()) {
            _uiState.update { it.copy(error = "Nama anak wajib diisi.") }
            return
        }
        val adminId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                // Soft MVP rule: one child per parent (not enforced in DB, so easy to relax later).
                if (childRepository.hasChild(s.selectedOrangtuaId)) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "Orang tua ini sudah memiliki data anak. Untuk saat ini, satu orang tua hanya bisa memiliki satu anak.",
                        )
                    }
                    return@launch
                }

                val anakId = childRepository.createChild(
                    ChildInsert(
                        nama = s.nama.trim(),
                        tanggalLahir = s.tanggalLahir?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        orangtuaId = s.selectedOrangtuaId,
                        catatanKondisi = s.catatan.ifBlank { null },
                        createdBy = adminId,
                    )
                )
                if (s.selectedRelawanId != null) {
                    childRepository.assignVolunteer(anakId, s.selectedRelawanId)
                }
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = ErrorText.of(e, "Gagal menyimpan data anak")) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateChildViewModel(
                    childRepository = ServiceLocator.childRepository,
                    profileRepository = ServiceLocator.profileRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
