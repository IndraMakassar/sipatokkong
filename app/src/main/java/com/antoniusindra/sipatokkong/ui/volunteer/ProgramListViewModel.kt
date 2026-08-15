package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProgramListUiState(
    val programs: List<Task> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val stoppingId: String? = null,
    val error: String? = null,
) {
    fun childNameOf(task: Task): String = childNames[task.anakId] ?: "Anak tidak dikenal"

    /** Recurring programs first — those are the ones that can be stopped. */
    val recurringPrograms: List<Task> get() = programs.filter { it.isRecurring }
    val oneOffTasks: List<Task> get() = programs.filter { !it.isRecurring }
}

class ProgramListViewModel(
    private val taskRepository: TaskRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgramListUiState())
    val uiState: StateFlow<ProgramListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val programs = taskRepository.programsForCurrentVolunteer()
                val children: List<ChildOption> = childRepository.childrenForCurrentVolunteer()
                programs to children.associate { it.id to it.nama }
            }.onSuccess { (programs, names) ->
                _uiState.update {
                    it.copy(
                        programs = programs,
                        childNames = names,
                        today = LocalDate.now(),
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Gagal memuat program ajar")
                }
            }
        }
    }

    /** Stops a program; from today on it produces no further occurrences. */
    fun stopProgram(tugasId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(stoppingId = tugasId, error = null) }
            runCatching { taskRepository.stopProgram(tugasId) }
                .onSuccess {
                    _uiState.update { it.copy(stoppingId = null) }
                    refresh()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(stoppingId = null, error = e.message ?: "Gagal menghentikan program")
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProgramListViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    childRepository = ServiceLocator.childRepository,
                )
            }
        }
    }
}
