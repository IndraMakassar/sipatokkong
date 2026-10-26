package com.antoniusindra.sipatokkong.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackReply
import com.antoniusindra.sipatokkong.data.model.FeedbackReplyInsert
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val currentProfile: () -> Profile?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackThreadUiState())
    val uiState: StateFlow<FeedbackThreadUiState> = _uiState.asStateFlow()

    private var feedbackId: String? = null
    private val refreshMutex = Mutex()

    /** Called by the screen's RESUMED lifecycle coroutine; cancellation stops the HTTP reads. */
    suspend fun refresh(id: String) {
        feedbackId = id
        val profile = currentProfile()
        _uiState.update {
            it.copy(myId = profile?.id, isParent = profile?.role == UserRole.ORANGTUA)
        }
        fetch()
    }

    fun retry() {
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() = refreshMutex.withLock {
        val id = feedbackId ?: return@withLock
        val previous = _uiState.value
        _uiState.update { it.copy(isLoading = it.feedback == null) }
        try {
            val fb = feedbackRepository.get(id) ?: error("Feedback tidak ditemukan.")
            val loaded = coroutineScope {
                val replies = async { feedbackRepository.replies(id) }
                // Names are stable context. Do not download profiles every ten seconds.
                val child = async {
                    previous.childName ?: optionalName { childRepository.getChild(fb.anakId)?.nama }
                }
                val parent = async {
                    if (previous.isParent) null else previous.parentName
                        ?: optionalName { profileRepository.getProfile(fb.orangtuaId)?.fullName }
                }
                Loaded(fb, replies.await(), child.await(), parent.await())
            }
            _uiState.update {
                it.copy(
                    isLoading = false, loadError = null,
                    feedback = loaded.feedback, replies = loaded.replies,
                    childName = loaded.childName, parentName = loaded.parentName,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Preserve messages and the composer when a background refresh fails.
            _uiState.update { it.copy(isLoading = false, loadError = ErrorText.of(e, "Gagal memuat")) }
        }
    }

    private suspend fun optionalName(read: suspend () -> String?): String? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, sendError = ErrorText.of(e, "Gagal mengirim balasan")) }
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
                    currentProfile = { (ServiceLocator.sessionManager.state.value as? SessionManager.State.SignedIn)?.profile },
                )
            }
        }
    }
}
