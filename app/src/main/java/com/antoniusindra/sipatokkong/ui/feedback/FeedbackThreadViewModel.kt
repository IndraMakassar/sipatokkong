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
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val childName: String? = null,
    /** Null for a parent viewing their own note: their own name would be noise. */
    val parentName: String? = null,
) {
    /** "Andi · Ibu Sari" for a volunteer, "Andi" for a parent, null while either is unknown. */
    val who: String? get() = FeedbackSummary.whoLine(childName, parentName)
}

class FeedbackThreadViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val childRepository: ChildRepository,
    private val profileRepository: ProfileRepository,
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
            val viewerIsParent = _uiState.value.isParent
            runCatching {
                val fb = feedbackRepository.get(id) ?: error("Feedback tidak ditemukan.")
                // The three reads that follow are independent of each other, so they go in
                // parallel -- same idiom and same justification as VolunteerHomeViewModel. They
                // cannot start before `fb` lands, because the ids they need are on it.
                coroutineScope {
                    val replies = async { feedbackRepository.replies(id) }
                    // getChild, not childrenForCurrentVolunteer: the latter filters is_active, so a
                    // note about a child who has since left the programme would lose its name --
                    // and that is exactly the old note whose context is hardest to reconstruct.
                    val child = async { runCatching { childRepository.getChild(fb.anakId) }.getOrNull() }
                    // Skipped for a parent: it is their own name, and this is a round trip.
                    val parent = async {
                        if (viewerIsParent) null
                        else runCatching { profileRepository.getProfile(fb.orangtuaId) }.getOrNull()
                    }
                    Loaded(fb, replies.await(), child.await()?.nama, parent.await()?.fullName)
                }
            }.onSuccess { loaded ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        feedback = loaded.feedback,
                        replies = loaded.replies,
                        childName = loaded.childName,
                        parentName = loaded.parentName,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "Gagal memuat") }
            }
        }
    }

    fun onReplyChange(v: String) = _uiState.update { it.copy(replyText = v, sendError = null) }

    /**
     * Sends [text], or whatever is in the composer when [text] is null.
     *
     * The quick-reply chips pass their sentence straight in rather than filling the text field
     * first, so a tap cannot be lost to a recomposition landing between the two updates. Both paths
     * share the [isSending] guard, the error handling and the [fetch] below, so a chip reply
     * refreshes the status pill exactly like a typed one.
     */
    fun sendReply(text: String? = null) {
        val s = _uiState.value
        val id = feedbackId ?: return
        val myId = s.myId
        val body = (text ?: s.replyText).trim()
        if (body.isBlank()) return
        if (s.isSending) return
        if (myId == null) { _uiState.update { it.copy(sendError = "Sesi tidak valid.") }; return }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, sendError = null) }
            try {
                feedbackRepository.addReply(
                    FeedbackReplyInsert(feedbackId = id, authorId = myId, isi = body)
                )
                // Only clear the composer when it was the source; a chip must not wipe a
                // half-typed message the volunteer decided to send separately.
                _uiState.update { it.copy(isSending = false, replyText = if (text == null) "" else it.replyText) }
                fetch() // reload replies + updated status
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = e.message ?: "Gagal mengirim balasan") }
            }
        }
    }

    /**
     * One load's results.
     *
     * The two name lookups are best-effort and land here as nulls on failure: a name is context on
     * a screen whose actual content is the note and its replies, and failing the whole load
     * because a profile row could not be read would hide the conversation to protect a label.
     */
    private data class Loaded(
        val feedback: Feedback,
        val replies: List<FeedbackReply>,
        val childName: String?,
        val parentName: String?,
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FeedbackThreadViewModel(
                    feedbackRepository = ServiceLocator.feedbackRepository,
                    childRepository = ServiceLocator.childRepository,
                    profileRepository = ServiceLocator.profileRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
