package com.antoniusindra.sipatokkong.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackListUiState(
    val items: List<Feedback> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class FeedbackListViewModel(private val feedbackRepository: FeedbackRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackListUiState())
    val uiState: StateFlow<FeedbackListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { feedbackRepository.list() }
                .onSuccess { list -> _uiState.update { it.copy(items = list, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message ?: "Gagal memuat feedback") } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { FeedbackListViewModel(ServiceLocator.feedbackRepository) }
        }
    }
}
