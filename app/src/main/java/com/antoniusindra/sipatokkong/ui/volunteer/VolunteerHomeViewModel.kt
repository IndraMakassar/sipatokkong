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

data class VolunteerHomeUiState(
    val programs: List<Task> = emptyList(),
    val children: List<ChildOption> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /**
     * Programs still producing work.
     *
     * Counting rows would overstate it — a volunteer with twenty finished programs is not carrying
     * twenty programs, and the number on the home card is meant to answer "what am I running now".
     *
     * One check, not two: `RecurrenceRule.effectiveEnd` already folds `stopped_at` into the end
     * date, so `isFinished` covers a stopped program as well as an expired one. Testing
     * `isStopped` separately reads as thorough but changes nothing.
     */
    val activePrograms: List<Task>
        get() = programs.filter { !it.toRecurrenceRule().isFinished(today) }

    val activeCount: Int get() = activePrograms.size

    val childCount: Int get() = children.size

    /** The card's one-line summary, kept here so its wording is unit-tested rather than eyeballed. */
    val summary: String
        get() = when {
            // Order matters when both are empty: no assigned child is the blocker to name, because
            // a program cannot be created without one. Leading with "no programs" would send the
            // volunteer to the create form, which they cannot complete.
            children.isEmpty() -> "Belum ada anak yang ditugaskan kepada Anda."
            activeCount == 0 -> "Belum ada program ajar yang berjalan."
            else -> "$activeCount program ajar sedang berjalan."
        }
}

/**
 * The volunteer's landing screen.
 *
 * No `init { refresh() }` on purpose: the screen drives loading via `LaunchedEffect`, so the counts
 * also reload when the volunteer returns from creating a program. Loading in both places would mean
 * two network round-trips on every entry — the same trap `ParentHomeViewModel` documents.
 */
class VolunteerHomeViewModel(
    private val taskRepository: TaskRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerHomeUiState())
    val uiState: StateFlow<VolunteerHomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val programs = taskRepository.programsForCurrentVolunteer()
                val children = childRepository.childrenForCurrentVolunteer()
                programs to children
            }.onSuccess { (programs, children) ->
                _uiState.update {
                    it.copy(
                        programs = programs,
                        children = children,
                        today = LocalDate.now(),
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Gagal memuat data")
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VolunteerHomeViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    childRepository = ServiceLocator.childRepository,
                )
            }
        }
    }
}
