package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.AssignedChild
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class VolunteerHomeUiState(
    val programs: List<Task> = emptyList(),
    val children: List<AssignedChild> = emptyList(),
    /** Parent full names by profile id, for the child cards. Missing ids simply show no parent. */
    val parentNames: Map<String, String> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /**
     * Programs still producing work.
     *
     * Counting rows would overstate it — a volunteer with twenty finished programs is not carrying
     * twenty programs, and the number on the home card is meant to answer "what am I running now".
     *
     * One check, not two: `RecurrenceRule.effectiveEnd` already folds `stopped_at` into the end
     * date, so `isFinished` covers a stopped program as well as an expired one. Testing
     * `isStopped` separately reads as thorough but changes nothing.
     */
    val activePrograms: List<Task>
        get() = programs.filter { !it.toRecurrenceRule().isFinished(today) }

    val activeCount: Int get() = activePrograms.size

    val childCount: Int get() = children.size

    /**
     * Programs still producing work **for one child**.
     *
     * Derived from [activePrograms], not from [programs]. The two must agree: the hero card above
     * shows [activeCount], so counting raw rows here would let the per-child numbers add up to more
     * than the total printed directly above them -- two figures on one screen contradicting each
     * other. `ui.programs` already carries `anakId`, so this costs no extra query.
     */
    fun activeProgramCount(child: AssignedChild): Int =
        activePrograms.count { it.anakId == child.id }

    /** Null rather than a placeholder: a home screen should drop the line, not apologise on it. */
    fun parentNameOf(child: AssignedChild): String? =
        child.orangtuaId?.let { parentNames[it] }

    /**
     * "6 tahun · Ibu Siti", with whichever half is missing left out.
     *
     * Joining unconditionally would leave a dangling "· Ibu Siti" for a child with no birth date --
     * and no child has ever had their birth date displayed before, so blanks are the normal case.
     */
    fun subtitleOf(child: AssignedChild, today: LocalDate = this.today): String =
        listOfNotNull(child.ageLabel(today), parentNameOf(child)).joinToString(" · ")

    /** The card's one-line summary, kept here so its wording is unit-tested rather than eyeballed. */
    val summary: String
        get() = when {
            // Order matters when both are empty: no assigned child is the blocker to name, because
            // a program cannot be created without one. Leading with "no programs" would send the
            // volunteer to the create form, which they cannot complete.
            children.isEmpty() -> "Belum ada anak yang ditugaskan kepada Anda."
            activeCount == 0 -> "Belum ada program ajar yang berjalan."
            else -> "$activeCount program ajar sedang berjalan."
        }
}

/**
 * The volunteer's landing screen.
 *
 * No `init { refresh() }` on purpose: the screen drives loading via `LaunchedEffect`, so the counts
 * also reload when the volunteer returns from creating a program. Loading in both places would mean
 * two network round-trips on every entry — the same trap `ParentHomeViewModel` documents.
 */
class VolunteerHomeViewModel(
    private val taskRepository: TaskRepository,
    private val childRepository: ChildRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerHomeUiState())
    val uiState: StateFlow<VolunteerHomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // Concurrent, not sequential. These three do not depend on each other, and this app
                // is built for slow connections -- run in parallel, three requests finish sooner
                // than the two that used to run one after the other. Do not "simplify" this back.
                coroutineScope {
                    val programs = async { taskRepository.programsForCurrentVolunteer() }
                    val children = async { childRepository.assignedChildrenForCurrentVolunteer() }
                    // RLS `can_view_profile` explicitly admits "I am a volunteer; target is the
                    // parent of a child assigned to me", so this returns exactly the parents of the
                    // children above and nothing else. No new policy needed.
                    val parents = async { profileRepository.profilesByRole("orangtua") }
                    Triple(programs.await(), children.await(), parents.await())
                }
            }.onSuccess { (programs, children, parents) ->
                _uiState.update {
                    it.copy(
                        programs = programs,
                        children = children,
                        parentNames = parents.associate { p -> p.id to p.fullName },
                        today = LocalDate.now(),
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Gagal memuat data")
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VolunteerHomeViewModel(
                    taskRepository = ServiceLocator.taskRepository,
                    childRepository = ServiceLocator.childRepository,
                    profileRepository = ServiceLocator.profileRepository,
                )
            }
        }
    }
}
