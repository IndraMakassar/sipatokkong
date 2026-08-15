package com.antoniusindra.sipatokkong.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.TaskOccurrence
import com.antoniusindra.sipatokkong.data.model.TaskOccurrences
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ParentHomeUiState(
    /** Occurrences from [PAST_WINDOW_DAYS] ago through [FUTURE_WINDOW_DAYS] ahead. */
    val occurrences: List<TaskOccurrence> = emptyList(),
    val childName: String? = null,
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /** Still to do: overdue occurrences plus everything from today onward. */
    val upcoming: List<TaskOccurrence>
        get() = occurrences.filter { !it.isDone }

    /** Grouped by due date, ascending — how the home screen renders them. */
    val upcomingByDate: List<Pair<LocalDate, List<TaskOccurrence>>>
        get() = upcoming.groupBy { it.date }.toList().sortedBy { it.first }

    /** Completed occurrences, most recent first, for the Riwayat tab. */
    val history: List<TaskOccurrence>
        get() = occurrences.filter { it.isDone }.sortedByDescending { it.date }

    /** Progress for the current Mon–Sun week. */
    val weekDone: Int get() = weekOccurrences.count { it.isDone }
    val weekTotal: Int get() = weekOccurrences.size

    private val weekOccurrences: List<TaskOccurrence>
        get() {
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            return occurrences.filter { it.date in monday..sunday }
        }

    companion object {
        const val PAST_WINDOW_DAYS = 30L
        const val FUTURE_WINDOW_DAYS = 14L
    }
}

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
                val completions = taskRepository.completionsForCurrentUser()
                val childName = childRepository.childForCurrentParent()?.nama
                Triple(tasks, completions, childName)
            }.onSuccess { (tasks, completions, childName) ->
                val today = LocalDate.now()
                val occurrences = TaskOccurrences.build(
                    tasks = tasks,
                    completions = completions,
                    from = today.minusDays(ParentHomeUiState.PAST_WINDOW_DAYS),
                    to = today.plusDays(ParentHomeUiState.FUTURE_WINDOW_DAYS),
                )
                _uiState.update {
                    it.copy(
                        occurrences = occurrences,
                        childName = childName,
                        today = today,
                        isLoading = false,
                    )
                }
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
