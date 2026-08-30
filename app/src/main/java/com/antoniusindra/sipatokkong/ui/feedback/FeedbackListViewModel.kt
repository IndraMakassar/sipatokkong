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
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedbackListUiState(
    val items: List<Feedback> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /**
     * `anak_id` -> name, and `orangtua_id` -> name.
     *
     * Empty for a parent, and not because the lookup failed: a parent has one child and every note
     * in this list is their own, so the names would restate what they already know at the cost of
     * two round trips on a connection this app is written to conserve. See [FeedbackListViewModel].
     */
    val childNames: Map<String, String> = emptyMap(),
    val parentNames: Map<String, String> = emptyMap(),
) {
    /**
     * Who a row is about, or null when nothing resolved -- which is the ordinary case for a parent
     * and the deactivated-account case for a volunteer.
     */
    fun whoOf(fb: Feedback): String? =
        FeedbackSummary.whoLine(childNames[fb.anakId], parentNames[fb.orangtuaId])
}

class FeedbackListViewModel(
    private val feedbackRepository: FeedbackRepository,
    private val childRepository: ChildRepository,
    private val profileRepository: ProfileRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackListUiState())
    val uiState: StateFlow<FeedbackListUiState> = _uiState.asStateFlow()

    // No init { refresh() }: the screen now drives loading via LaunchedEffect so the list also
    // reloads on re-entry. Doing both would mean two round-trips on every visit.

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // Only a volunteer needs the names, so only a volunteer pays for them. A parent
                // sees one child's notes and all of them are theirs.
                //
                // Three requests in parallel rather than in sequence, the same idiom and for the
                // same stated reason as VolunteerHomeViewModel: on a slow connection the difference
                // between one round trip and three is the whole wait.
                if (sessionManager.currentRole == UserRole.RELAWAN) {
                    coroutineScope {
                        val notes = async { feedbackRepository.list() }
                        val children = async { childRepository.visibleChildOptions() }
                        val parents = async { profileRepository.profilesByRoleFull("orangtua") }
                        Triple(notes.await(), children.await(), parents.await())
                    }
                } else {
                    // Spelled out rather than inferred: the two branches of this `if` have to agree
                    // on one Triple type, and naming it here is what makes that legible.
                    Triple(feedbackRepository.list(), emptyList<ChildOption>(), emptyList<Profile>())
                }
            }
                .onSuccess { (list, children, parents) ->
                    _uiState.update {
                        it.copy(
                            items = list,
                            isLoading = false,
                            childNames = children.associate { c -> c.id to c.nama },
                            parentNames = parents.associate { p -> p.id to p.fullName },
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat feedback")) } }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                FeedbackListViewModel(
                    feedbackRepository = ServiceLocator.feedbackRepository,
                    childRepository = ServiceLocator.childRepository,
                    profileRepository = ServiceLocator.profileRepository,
                    sessionManager = ServiceLocator.sessionManager,
                )
            }
        }
    }
}
