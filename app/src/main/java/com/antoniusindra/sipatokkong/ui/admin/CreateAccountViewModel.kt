package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.AuthRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateAccountUiState(
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val phone: String = "",
    val familyStatus: String = "",
    val address: String = "",
    val quotaNotes: String = "",
    val specialization: String = "",
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null
)

class CreateAccountViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateAccountUiState())
    val uiState: StateFlow<CreateAccountUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) = _uiState.update { it.copy(email = email) }
    fun onPasswordChange(password: String) = _uiState.update { it.copy(password = password) }
    fun onFullNameChange(name: String) = _uiState.update { it.copy(fullName = name) }
    fun onPhoneChange(phone: String) = _uiState.update { it.copy(phone = phone) }
    fun onFamilyStatusChange(status: String) = _uiState.update { it.copy(familyStatus = status) }
    fun onAddressChange(address: String) = _uiState.update { it.copy(address = address) }
    fun onQuotaNotesChange(notes: String) = _uiState.update { it.copy(quotaNotes = notes) }
    fun onSpecializationChange(spec: String) = _uiState.update { it.copy(specialization = spec) }

    fun register(role: UserRole) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank() || state.fullName.isBlank()) {
            _uiState.update { it.copy(error = "Harap isi semua bidang yang wajib (Email, Password, Nama Lengkap)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Sign up the user in Supabase Auth
                val userId = authRepository.signUp(state.email, state.password)

                // 2. Get current admin ID
                val adminId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id

                // 3. Create the profile in Postgrest
                val profile = Profile(
                    id = userId,
                    role = role,
                    fullName = state.fullName,
                    phone = state.phone.ifBlank { null },
                    familyStatus = state.familyStatus.ifBlank { null },
                    address = state.address.ifBlank { null },
                    quotaNotes = state.quotaNotes.ifBlank { null },
                    specialization = state.specialization.ifBlank { null },
                    createdBy = adminId
                )
                profileRepository.createProfile(profile)

                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Gagal membuat akun") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateAccountViewModel(
                    authRepository = ServiceLocator.registrationRepository,
                    profileRepository = ServiceLocator.profileRepository,
                    sessionManager = ServiceLocator.sessionManager
                )
            }
        }
    }
}
