package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.TaskSubmission
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TaskSubmissionsUiState(
    val submissions: List<TaskSubmission> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val today: LocalDate = LocalDate.now(),
)

class TaskSubmissionsViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskSubmissionsUiState())
    val uiState: StateFlow<TaskSubmissionsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                taskRepository.submissionsForCurrentVolunteer()
            }.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        submissions = list,
                        isLoading = false,
                        today = LocalDate.now(),
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat daftar setoran tugas"))
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TaskSubmissionsViewModel(
                    taskRepository = ServiceLocator.taskRepository
                )
            }
        }
    }
}
