package com.antoniusindra.sipatokkong.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.TaskOccurrence
import com.antoniusindra.sipatokkong.data.model.TaskOccurrences
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.NotificationRepository
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

    /**
     * Unread notifications, for the badge on the bell. Loaded independently of the task list —
     * see [ParentHomeViewModel.refreshUnread].
     */
    val unreadNotifications: Int = 0,
) {
    /**
     * What the home screen shows.
     *
     * Today and later always appear — including finished ones, so a parent can still see what
     * they got done today instead of the card silently vanishing. Past dates only appear while
     * still unfinished (i.e. overdue); once done they belong to the history screen.
     */
    val visible: List<TaskOccurrence>
        get() = occurrences.filter { it.date >= today || !it.isDone }

    /** Grouped by due date, ascending — how the home screen renders them. */
    val visibleByDate: List<Pair<LocalDate, List<TaskOccurrence>>>
        get() = visible.groupBy { it.date }.toList().sortedBy { it.first }

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
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentHomeUiState())
    val uiState: StateFlow<ParentHomeUiState> = _uiState.asStateFlow()

    // No init { refresh() } on purpose: the screen drives loading via LaunchedEffect so the list
    // also reloads when the user returns from the task detail screen. Loading here as well would
    // mean two network round-trips on every entry — costly for a deliberately data-frugal app.

    fun refresh() {
        refreshUnread()
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
                _uiState.update { it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat data")) }
            }
        }
    }

    /**
     * The unread count behind the bell badge.
     *
     * Its own launch and its own `runCatching`, deliberately kept out of [refresh]'s block: that
     * one is all-or-nothing, so a failing notifications query would wipe the parent's entire task
     * list off the home screen. It also sets no error on failure — a badge that did not load is not
     * worth a full-screen error over the thing the parent actually opened the app for. The count
     * simply stays where it was.
     *
     * Runs on every [refresh], including the one `parent_history` triggers, where the badge is not
     * even on screen. That is one extra request with no response body (`head = true`), which is
     * cheaper than a flag in this ViewModel's public API to avoid it.
     */
    private fun refreshUnread() {
        viewModelScope.launch {
            runCatching { notificationRepository.unreadCountForCurrentUser() }
                .onSuccess { count -> _uiState.update { it.copy(unreadNotifications = count) } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ParentHomeViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    childRepository = ServiceLocator.childRepository,
                    notificationRepository = ServiceLocator.notificationRepository,
                )
            }
        }
    }
}
