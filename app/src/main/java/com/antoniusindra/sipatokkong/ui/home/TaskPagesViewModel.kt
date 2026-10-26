package com.antoniusindra.sipatokkong.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.TaskPageRow
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Separate state from home/progress, so paging or filtering cannot clear the dashboard. */
data class TaskPageState(
    val rows: List<TaskPageRow> = emptyList(),
    val from: String? = null,
    val to: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
)

class TaskPagesViewModel(
    private val repository: TaskRepository,
    private val overdue: Boolean = false,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val state = MutableStateFlow(TaskPageState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var retryAppend = false

    fun ensureLoaded() { if (!state.value.loaded && !state.value.loading) load(false) }
    fun refresh() = load(false)
    fun more() { if (state.value.hasMore && !state.value.loading) load(true) }
    fun retry() = load(retryAppend)
    fun filter(from: String?, to: String?) {
        if (from != null && to != null && from > to) {
            state.update { it.copy(error = "Tanggal awal harus sebelum atau sama dengan tanggal akhir.") }
            return
        }
        job?.cancel()
        state.value = TaskPageState(from = from, to = to)
        load(false)
    }
    private fun load(append: Boolean) {
        job?.cancel()
        retryAppend = append
        job = viewModelScope.launch {
            val snapshot = state.value
            state.update { it.copy(loading = true, error = null) }
            try {
                var cursor = if (append) snapshot.rows.lastOrNull() else null
                val fetched = mutableListOf<TaskPageRow>()
                val targetSize = if (append) 50 else maxOf(50, snapshot.rows.size)
                var hasMore: Boolean
                do {
                    val page = if (overdue) repository.oldOverduePage(today().minusDays(30).toString(), cursor)
                        else repository.historyPage(snapshot.from, snapshot.to, cursor)
                    fetched.addAll(page.take(50))
                    hasMore = page.size > 50
                    cursor = fetched.lastOrNull()
                } while (hasMore && fetched.size < targetSize)
                state.update { it.copy(
                    rows = ((if (append) snapshot.rows else emptyList()) + fetched).distinctBy { it.key },
                    loading = false, loaded = true, hasMore = hasMore,
                ) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                state.update { it.copy(loading = false, error = ErrorText.of(e, "Gagal memuat tugas")) }
            }
        }
    }
    companion object {
        fun factory(overdue: Boolean = false): ViewModelProvider.Factory = viewModelFactory {
            initializer { TaskPagesViewModel(ServiceLocator.taskRepository, overdue) }
        }
    }
}
