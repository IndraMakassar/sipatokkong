package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditAccountUiState(
    val profile: Profile? = null,
    val fullName: String = "",
    val phone: String = "",
    val familyStatus: String = "",
    val address: String = "",
    val quotaNotes: String = "",
    val specialization: String = "",
    val isActive: Boolean = true,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
)

/**
 * Edits a single row of `public.profiles`.
 *
 * Email and password are deliberately absent: changing another user's credentials requires the
 * Supabase Admin API with the `service_role` key, which never ships in the APK.
 */
class EditAccountViewModel(
    private val profileId: String,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                profileRepository.getProfile(profileId)
            }.onSuccess { profile ->
                if (profile == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Akun tidak ditemukan.")
                    }
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        profile = profile,
                        fullName = profile.fullName,
                        phone = profile.phone.orEmpty(),
                        familyStatus = profile.familyStatus.orEmpty(),
                        address = profile.address.orEmpty(),
                        quotaNotes = profile.quotaNotes.orEmpty(),
                        specialization = profile.specialization.orEmpty(),
                        isActive = profile.isActive,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat akun"))
                }
            }
        }
    }

    fun onFullNameChange(v: String) = _uiState.update { it.copy(fullName = v, error = null) }
    fun onPhoneChange(v: String) = _uiState.update { it.copy(phone = v) }
    fun onFamilyStatusChange(v: String) = _uiState.update { it.copy(familyStatus = v) }
    fun onAddressChange(v: String) = _uiState.update { it.copy(address = v) }
    fun onQuotaNotesChange(v: String) = _uiState.update { it.copy(quotaNotes = v) }
    fun onSpecializationChange(v: String) = _uiState.update { it.copy(specialization = v) }
    fun onActiveChange(v: Boolean) = _uiState.update { it.copy(isActive = v) }

    fun save() {
        val s = _uiState.value
        val current = s.profile ?: return
        if (s.fullName.isBlank()) {
            _uiState.update { it.copy(error = "Nama lengkap wajib diisi.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                // role and createdBy are carried over untouched.
                profileRepository.updateProfile(
                    current.copy(
                        fullName = s.fullName.trim(),
                        phone = s.phone.ifBlank { null },
                        familyStatus = s.familyStatus.ifBlank { null },
                        address = s.address.ifBlank { null },
                        quotaNotes = s.quotaNotes.ifBlank { null },
                        specialization = s.specialization.ifBlank { null },
                        isActive = s.isActive,
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isSubmitting = false, error = ErrorText.of(e, "Gagal menyimpan perubahan"))
                }
            }
        }
    }

    companion object {
        fun factory(profileId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditAccountViewModel(
                    profileId = profileId,
                    profileRepository = ServiceLocator.profileRepository,
                )
            }
        }
    }
}
