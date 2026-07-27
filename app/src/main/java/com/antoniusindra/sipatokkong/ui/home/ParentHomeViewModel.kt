package com.antoniusindra.sipatokkong.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Tugas
import com.antoniusindra.sipatokkong.data.repository.TugasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentHomeUiState(
    val tasks: List<Tugas> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ParentHomeViewModel(private val tugasRepository: TugasRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentHomeUiState())
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { tugasRepository.tasksForCurrentUser() }
                .onSuccess { list -> _uiState.update { it.copy(tasks = list, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Gagal memuat tugas") } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { ParentHomeViewModel(ServiceLocator.tugasRepository) }
        }
    }
}
