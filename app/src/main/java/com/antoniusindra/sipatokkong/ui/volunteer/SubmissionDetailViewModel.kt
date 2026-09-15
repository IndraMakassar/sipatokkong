package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.media.MediaBucket
import com.antoniusindra.sipatokkong.core.media.MediaDownloadHandler
import com.antoniusindra.sipatokkong.core.media.MediaRef
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.Documentation
import com.antoniusindra.sipatokkong.data.model.TaskSubmission
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubmissionDetailUiState(
    val submission: TaskSubmission? = null,
    val documentation: Documentation? = null,
    val documentationUrl: String? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    
    val feedbackInput: String = "",
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val isSuccess: Boolean = false,
)

class SubmissionDetailViewModel(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmissionDetailUiState())
    val uiState: StateFlow<SubmissionDetailUiState> = _uiState.asStateFlow()

    val download = MediaDownloadHandler(viewModelScope)

    fun proofRef(): MediaRef? =
        _uiState.value.documentation?.let { MediaRef(MediaBucket.DOKUMENTASI, it.mediaPath) }

    fun load(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val sub = taskRepository.submissionFor(id)
                if (sub == null) return@runCatching Triple(null, null, null)
                
                var doc: Documentation? = null
                var url: String? = null
                
                if (sub.dokumentasiId != null) {
                    doc = taskRepository.getDocumentation(sub.dokumentasiId)
                    if (doc != null) {
                        url = taskRepository.signedDocumentationUrl(doc.mediaPath)
                    }
                }
                Triple(sub, doc, url)
            }.onSuccess { (sub, doc, url) ->
                if (sub == null) {
                    _uiState.update { it.copy(isLoading = false, loadError = "Setoran tugas tidak ditemukan") }
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        submission = sub,
                        documentation = doc,
                        documentationUrl = url,
                        feedbackInput = sub.feedbackRelawan ?: "",
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, loadError = ErrorText.of(e, "Gagal memuat detail setoran"))
                }
            }
        }
    }

    fun onFeedbackChange(text: String) {
        _uiState.update { it.copy(feedbackInput = text) }
    }

    fun submitFeedback() {
        val subId = _uiState.value.submission?.id ?: return
        val text = _uiState.value.feedbackInput.trim().ifBlank { null }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null, isSuccess = false) }
            runCatching {
                taskRepository.updateFeedback(subId, text)
            }.onSuccess {
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isSubmitting = false, submitError = ErrorText.of(e, "Gagal menyimpan feedback"))
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SubmissionDetailViewModel(
                    taskRepository = ServiceLocator.taskRepository
                )
            }
        }
    }
}
