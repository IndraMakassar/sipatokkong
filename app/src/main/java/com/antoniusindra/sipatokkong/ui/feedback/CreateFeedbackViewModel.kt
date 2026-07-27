package com.antoniusindra.sipatokkong.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.FeedbackInsert
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateFeedbackUiState(
    val child: ChildOption? = null,
    val isLoadingChild: Boolean = true,
    val catatanPerilaku: String = "",
    val kendala: String = "",
    val isi: String = "",
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
)

class CreateFeedbackViewModel(
    private val childRepository: ChildRepository,
    private val feedbackRepository: FeedbackRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateFeedbackUiState())
    val uiState: StateFlow<CreateFeedbackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChild = true) }
            runCatching { childRepository.childForCurrentParent() }
                .onSuccess { child -> _uiState.update { it.copy(child = child, isLoadingChild = false) } }
                .onFailure { e -> _uiState.update { it.copy(isLoadingChild = false, error = e.message) } }
        }
    }

    fun onPerilakuChange(v: String) = _uiState.update { it.copy(catatanPerilaku = v) }
    fun onKendalaChange(v: String) = _uiState.update { it.copy(kendala = v) }
    fun onIsiChange(v: String) = _uiState.update { it.copy(isi = v, error = null) }

    fun submit() {
        val s = _uiState.value
        val orangtuaId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        when {
            s.child == null -> { _uiState.update { it.copy(error = "Data anak belum tersedia.") }; return }
            s.isi.isBlank() -> { _uiState.update { it.copy(error = "Catatan/feedback utama wajib diisi.") }; return }
            orangtuaId == null -> { _uiState.update { it.copy(error = "Sesi tidak valid. Login ulang.") }; return }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                feedbackRepository.create(
                    FeedbackInsert(
                        anakId = s.child!!.id,
                        orangtuaId = orangtuaId!!,
                        catatanPerilaku = s.catatanPerilaku.ifBlank { null },
                        kendala = s.kendala.ifBlank { null },
                        isi = s.isi.trim(),
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = e.message ?: "Gagal mengirim feedback") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateFeedbackViewModel(
                    childRepository = ServiceLocator.childRepository,
                    feedbackRepository = ServiceLocator.feedbackRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
