package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskTemplateInsert
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
    val today: LocalDate = LocalDate.now(java.time.ZoneId.of("Asia/Makassar")),
    val isLoading: Boolean = true,
    val stoppingId: String? = null,
    /** Id of the program currently being turned into a template, if any. */
    val savingTemplateFor: String? = null,
    /** Set briefly after a template is saved, so the screen can confirm it. */
    val templateSavedName: String? = null,
    /** A failed *load*. Replaces the list, because there is nothing to show. */
    val error: String? = null,
    /**
     * A failed *action*, kept apart from [error] on purpose.
     *
     * Both used to share one field, so a failed "Hentikan Program" wiped the whole list and left a
     * full-screen red message — the volunteer lost everything they were looking at because one
     * button failed.
     */
    val actionError: String? = null,
) {
    fun childNameOf(task: Task): String = childNames[task.anakId] ?: "Anak tidak dikenal"

    /** Recurring programs first — those are the ones that can be stopped. */
    val recurringPrograms: List<Task> get() = programs.filter { it.isRecurring }
    val oneOffTasks: List<Task> get() = programs.filter { !it.isRecurring }
}

class ProgramListViewModel(
    private val taskRepository: TaskRepository,
    private val childRepository: ChildRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgramListUiState())
    val uiState: StateFlow<ProgramListUiState> = _uiState.asStateFlow()

    // No init { refresh() }: ProgramListScreen already calls refresh() from a LaunchedEffect, so
    // having both meant two network round-trips on every entry.

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
                        today = LocalDate.now(java.time.ZoneId.of("Asia/Makassar")),
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat program ajar"))
                }
            }
        }
    }

    /** Stops a program; from today on it produces no further occurrences. */
    fun stopProgram(tugasId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(stoppingId = tugasId, actionError = null) }
            runCatching { taskRepository.stopProgram(tugasId) }
                .onSuccess {
                    _uiState.update { it.copy(stoppingId = null) }
                    refresh()
                }
                .onFailure { e ->
                    // actionError, not error: the list stays on screen.
                    _uiState.update {
                        it.copy(
                            stoppingId = null,
                            actionError = ErrorText.of(e, "Gagal menghentikan program"),
                        )
                    }
                }
        }
    }

    fun cancelTask(task: Task) {
        if (_uiState.value.stoppingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(stoppingId = task.id, actionError = null) }
            try {
                taskRepository.cancelOneOff(task)
                _uiState.update { it.copy(stoppingId = null) }
                refresh()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _uiState.update { it.copy(stoppingId = null, actionError = ErrorText.of(e, "Gagal membatalkan tugas")) } }
        }
    }

    /**
     * Turns an existing program into a reusable template.
     *
     * Cheapest possible path: the demo media is copied `tugas-demo` -> `tugas-template` on the
     * server, so nothing is downloaded or uploaded from the phone at all.
     *
     * `TaskTemplateInsert.from` is what drops anak_id and every date — the split between the
     * exercise and this particular delivery of it lives there, next to a test, rather than here.
     */
    fun saveAsTemplate(task: Task, nama: String) {
        val relawanId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        if (relawanId == null) {
            _uiState.update { it.copy(actionError = "Sesi tidak valid. Silakan login ulang.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(savingTemplateFor = task.id, actionError = null) }
            runCatching {
                val path = taskRepository.copyDemoToTemplate(task.mediaPath, relawanId)
                taskRepository.createTemplate(TaskTemplateInsert.from(task, nama, relawanId, path))
            }.onSuccess {
                _uiState.update {
                    it.copy(savingTemplateFor = null, templateSavedName = nama.trim())
                }
            }.onFailure { e ->
                // actionError, not error: the list stays on screen.
                _uiState.update {
                    it.copy(
                        savingTemplateFor = null,
                        actionError = ErrorText.of(e, "Gagal membuat template"),
                    )
                }
            }
        }
    }

    fun dismissTemplateSaved() = _uiState.update { it.copy(templateSavedName = null) }

    fun dismissActionError() = _uiState.update { it.copy(actionError = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProgramListViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    childRepository = ServiceLocator.childRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
