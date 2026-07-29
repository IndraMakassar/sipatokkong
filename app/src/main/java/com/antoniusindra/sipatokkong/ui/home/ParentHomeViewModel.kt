package com.antoniusindra.sipatokkong.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentHomeUiState(
    val tasks: List<Task> = emptyList(),
    val childName: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ParentHomeViewModel(
    private val taskRepository: TaskRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentHomeUiState())
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val tasks = taskRepository.tasksForCurrentUser()
                val childName = childRepository.childForCurrentParent()?.nama
                tasks to childName
            }.onSuccess { (tasks, childName) ->
                _uiState.update { it.copy(tasks = tasks, childName = childName, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Gagal memuat data") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ParentHomeViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    childRepository = ServiceLocator.childRepository,
                )
            }
        }
    }
}
