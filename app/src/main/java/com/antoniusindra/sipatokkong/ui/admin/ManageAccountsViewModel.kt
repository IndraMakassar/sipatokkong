package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageAccountsUiState(
    val profiles: List<Profile> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** Name/phone search, case-insensitive. Empty query shows everything. */
    val visibleProfiles: List<Profile>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return profiles
            return profiles.filter {
                it.fullName.contains(q, ignoreCase = true) ||
                    (it.phone?.contains(q, ignoreCase = true) == true)
            }
        }
}

class ManageAccountsViewModel(
    private val role: UserRole,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageAccountsUiState())
    val uiState: StateFlow<ManageAccountsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    /** Re-reads the list. Called on first composition and whenever we return from the edit screen. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                profileRepository.profilesByRoleFull(role.name.lowercase())
            }.onSuccess { list ->
                _uiState.update { it.copy(profiles = list, isLoading = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Gagal memuat daftar akun")
                }
            }
        }
    }

    companion object {
        fun factory(role: UserRole): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ManageAccountsViewModel(
                    role = role,
                    profileRepository = ServiceLocator.profileRepository,
                )
            }
        }
    }
}
