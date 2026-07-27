package com.antoniusindra.sipatokkong.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackReply
import com.antoniusindra.sipatokkong.data.model.FeedbackReplyInsert
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackThreadUiState(
    val isLoading: Boolean = true,
    val feedback: Feedback? = null,
    val replies: List<FeedbackReply> = emptyList(),
    val loadError: String? = null,
    val myId: String? = null,
    val isParent: Boolean = true,
    val replyText: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null,
)

class FeedbackThreadViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackThreadUiState())
    val uiState: StateFlow<FeedbackThreadUiState> = _uiState.asStateFlow()

    private var feedbackId: String? = null
    private var loaded = false

    fun load(id: String) {
        if (loaded) return
        loaded = true
        feedbackId = id
        val signed = sessionManager.state.value as? SessionManager.State.SignedIn
        _uiState.update {
            it.copy(myId = signed?.profile?.id, isParent = signed?.profile?.role == UserRole.ORANGTUA)
        }
        fetch()
    }

    private fun fetch() {
        val id = feedbackId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            runCatching {
                val fb = feedbackRepository.get(id) ?: error("Feedback tidak ditemukan.")
                val replies = feedbackRepository.replies(id)
                fb to replies
            }.onSuccess { (fb, replies) ->
                _uiState.update { it.copy(isLoading = false, feedback = fb, replies = replies) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "Gagal memuat") }
            }
        }
    }

    fun onReplyChange(v: String) = _uiState.update { it.copy(replyText = v, sendError = null) }

    fun sendReply() {
        val s = _uiState.value
        val id = feedbackId ?: return
        val myId = s.myId
        if (s.replyText.isBlank()) return
        if (myId == null) { _uiState.update { it.copy(sendError = "Sesi tidak valid.") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                feedbackRepository.addReply(
                    FeedbackReplyInsert(feedbackId = id, authorId = myId, isi = s.replyText.trim())
                )
                _uiState.update { it.copy(isSending = false, replyText = "") }
                fetch() // reload replies + updated status
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message ?: "Gagal mengirim balasan") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FeedbackThreadViewModel(
                    feedbackRepository = ServiceLocator.feedbackRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
