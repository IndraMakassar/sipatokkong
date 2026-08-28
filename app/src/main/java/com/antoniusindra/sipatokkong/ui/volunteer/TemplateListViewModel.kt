package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.TaskTemplate
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateListUiState(
    val templates: List<TaskTemplate> = emptyList(),
    /** Who is looking, so the list can mark and gate the ones they own. */
    val myId: String? = null,
    val isLoading: Boolean = true,
    val deletingId: String? = null,
    /** A failed *load*. Replaces the list, because there is nothing to show. */
    val error: String? = null,
    /** A failed *action*, kept apart from [error] so a failed delete leaves the list on screen. */
    val actionError: String? = null,
) {
    /** Only the author may change or delete a template — mirrors the `template_delete` policy. */
    fun isMine(template: TaskTemplate): Boolean = myId != null && template.relawanId == myId
}

class TemplateListViewModel(
    private val taskRepository: TaskRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateListUiState())
    val uiState: StateFlow<TemplateListUiState> = _uiState.asStateFlow()

    // No init { refresh() }: the screen drives loading from a LaunchedEffect, so having both would
    // mean two round-trips per entry. Same reasoning as ProgramListViewModel.

    fun refresh() {
        viewModelScope.launch {
            val signed = sessionManager.state.value as? SessionManager.State.SignedIn
            _uiState.update { it.copy(isLoading = true, error = null, myId = signed?.profile?.id) }
            runCatching { taskRepository.templates() }
                .onSuccess { list ->
                    _uiState.update { it.copy(templates = list, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Gagal memuat template")
                    }
                }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = id, actionError = null) }
            runCatching { taskRepository.deleteTemplate(id) }
                .onSuccess {
                    _uiState.update { it.copy(deletingId = null) }
                    refresh()
                }
                .onFailure { e ->
                    // actionError, not error: the list stays on screen.
                    _uiState.update {
                        it.copy(deletingId = null, actionError = e.message ?: "Gagal menghapus template")
                    }
                }
        }
    }

    fun dismissActionError() = _uiState.update { it.copy(actionError = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TemplateListViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
