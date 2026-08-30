package com.antoniusindra.sipatokkong.ui.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.antoniusindra.sipatokkong.core.net.Connectivity

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class LoginViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, error = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    fun login() {
        val currentState = _uiState.value
        if (currentState.email.isBlank() || currentState.password.isBlank()) {
            _uiState.update { it.copy(error = "Email dan password tidak boleh kosong") }
            return
        }

        // Asked before the attempt, not after it. Without this the user waits out a connection
        // timeout to be told something the phone already knew, and on an upload they burn data
        // getting there. A false positive is possible -- see Connectivity -- so the mapped
        // error on the way back is still what has the last word.
        if (!Connectivity.isOnline(ServiceLocator.appContext)) {
            _uiState.update { it.copy(error = ErrorText.OFFLINE) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                repository.signIn(currentState.email, currentState.password)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = ErrorText.of(e, "Terjadi kesalahan")) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(ServiceLocator.authRepository)
            }
        }
    }
}
