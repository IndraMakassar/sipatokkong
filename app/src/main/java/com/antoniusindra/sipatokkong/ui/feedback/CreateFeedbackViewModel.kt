package com.antoniusindra.sipatokkong.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
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
    val mood: String? = null,
    val selectedChallenges: Set<String> = emptySet(),
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
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoadingChild = false, error = ErrorText.of(e, "Gagal memuat data anak"))
                    }
                }
        }
    }

    fun onMoodChange(v: String) = _uiState.update { it.copy(mood = v) }

    fun toggleChallenge(challenge: String) {
        _uiState.update { s ->
            val next = if (s.selectedChallenges.contains(challenge)) {
                s.selectedChallenges - challenge
            } else {
                s.selectedChallenges + challenge
            }
            s.copy(selectedChallenges = next)
        }
    }

    fun onIsiChange(v: String) = _uiState.update { it.copy(isi = v, error = null) }

    fun submit() {
        val s = _uiState.value
        val orangtuaId = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id
        when {
            s.child == null -> { _uiState.update { it.copy(error = "Data anak belum tersedia.") }; return }
            s.mood == null -> { _uiState.update { it.copy(error = "Pilih kondisi mood anak terlebih dahulu.") }; return }
            s.isi.isBlank() && s.selectedChallenges.isEmpty() -> {
                _uiState.update { it.copy(error = "Berikan catatan atau pilih kendala yang dialami.") }
                return
            }
            orangtuaId == null -> { _uiState.update { it.copy(error = "Sesi tidak valid. Login ulang.") }; return }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                // We simplify the input by combining challenges into kendala or prefixing isi
                val kendalaText = if (s.selectedChallenges.isNotEmpty()) {
                    s.selectedChallenges.joinToString(", ")
                } else null

                feedbackRepository.create(
                    FeedbackInsert(
                        anakId = s.child.id,
                        orangtuaId = orangtuaId,
                        catatanPerilaku = null, // Simplified UI removes this field
                        kendala = kendalaText,
                        mood = s.mood,
                        isi = s.isi.trim().ifBlank { CATATAN_RUTIN },
                    )
                )
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSubmitting = false, error = ErrorText.of(e, "Gagal mengirim feedback")) }
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
