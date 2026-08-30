package com.antoniusindra.sipatokkong.ui.home

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

data class ProfileUiState(
    val profile: Profile? = null,
    val isEditing: Boolean = false,
    val editedName: String = "",
    val editedPhone: String = "",
    val editedAddress: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class ProfileViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun initProfile(profile: Profile) {
        if (_uiState.value.profile == null) {
            _uiState.update {
                it.copy(
                    profile = profile,
                    editedName = profile.fullName,
                    editedPhone = profile.phone ?: "",
                    editedAddress = profile.address ?: ""
                )
            }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true, isSuccess = false, error = null) }
    }

    fun cancelEditing() {
        val currentProfile = _uiState.value.profile
        _uiState.update {
            it.copy(
                isEditing = false,
                editedName = currentProfile?.fullName ?: "",
                editedPhone = currentProfile?.phone ?: "",
                editedAddress = currentProfile?.address ?: ""
            )
        }
    }

    fun onNameChange(newName: String) {
        _uiState.update { it.copy(editedName = newName) }
    }

    fun onPhoneChange(newPhone: String) {
        _uiState.update { it.copy(editedPhone = newPhone) }
    }

    fun onAddressChange(newAddress: String) {
        _uiState.update { it.copy(editedAddress = newAddress) }
    }

    fun saveProfile() {
        val currentState = _uiState.value
        val currentProfile = currentState.profile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val updatedProfile = currentProfile.copy(
                    fullName = currentState.editedName,
                    phone = currentState.editedPhone,
                    address = currentState.editedAddress
                )
                profileRepository.updateProfile(updatedProfile)
                updatedProfile
            }.onSuccess { updated ->
                _uiState.update {
                    it.copy(
                        profile = updated,
                        isEditing = false,
                        isLoading = false,
                        isSuccess = true
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memperbarui profil")) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    profileRepository = ServiceLocator.profileRepository
                )
            }
        }
    }
}
