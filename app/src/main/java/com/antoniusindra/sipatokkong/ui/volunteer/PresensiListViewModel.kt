package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.core.media.MediaBucket
import com.antoniusindra.sipatokkong.core.media.MediaDownloadHandler
import com.antoniusindra.sipatokkong.core.media.MediaRef
import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.PresensiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PresensiListUiState(
    val visits: List<Presensi> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    /** Which card is opened. Only the opened card loads its photo — the list stays data-frugal. */
    val expandedId: String? = null,
    /** Signed photo URLs, cached per visit so re-opening a card costs nothing. */
    val photoUrls: Map<String, String> = emptyMap(),
    val loadingPhotoId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    fun childNameOf(visit: Presensi): String = childNames[visit.anakId] ?: "Anak tidak dikenal"

    /** Visits logged this calendar month — the figure a volunteer is usually asked for. */
    val thisMonthCount: Int
        get() = visits.count {
            val d = it.parsedDate ?: return@count false
            d.year == today.year && d.month == today.month
        }
}

/**
 * The volunteer's own visit history.
 *
 * No `init { refresh() }`: the screen drives loading from a `LaunchedEffect`, so the list also
 * reloads when the volunteer comes back from the form. Doing both would mean two round-trips per
 * entry — the trap `ProgramListViewModel` documents.
 */
class PresensiListViewModel(
    private val presensiRepository: PresensiRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresensiListUiState())
    val uiState: StateFlow<PresensiListUiState> = _uiState.asStateFlow()

    /** Saving a proof photo to the gallery. */
    val download = MediaDownloadHandler(viewModelScope)

    /**
     * Opens or closes one visit card, fetching its signed photo URL the first time it is opened.
     *
     * Loading photos with the list would mean downloading every proof photo just to scroll —
     * nothing writes `foto_thumb_path`, so there are no thumbnails to fall back on.
     */
    fun toggleExpanded(visit: Presensi) {
        val current = _uiState.value
        if (current.expandedId == visit.id) {
            _uiState.update { it.copy(expandedId = null) }
            return
        }

        _uiState.update { it.copy(expandedId = visit.id) }

        val path = visit.fotoPath ?: return
        if (current.photoUrls.containsKey(visit.id)) return

        viewModelScope.launch {
            _uiState.update { it.copy(loadingPhotoId = visit.id) }
            runCatching { presensiRepository.signedFotoUrl(path) }
                .onSuccess { url ->
                    _uiState.update {
                        it.copy(photoUrls = it.photoUrls + (visit.id to url), loadingPhotoId = null)
                    }
                }
                .onFailure {
                    // The card still opens; only the preview is missing, and the download button
                    // goes through a separate authenticated path that does not need this URL.
                    _uiState.update { it.copy(loadingPhotoId = null) }
                }
        }
    }

    /** The proof photo of one visit, as a downloadable reference. */
    fun photoRef(visit: Presensi): MediaRef? =
        visit.fotoPath?.let { MediaRef(MediaBucket.PRESENSI, it) }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val visits = presensiRepository.listForCurrentVolunteer()
                val children = childRepository.childrenForCurrentVolunteer()
                visits to children.associate { it.id to it.nama }
            }.onSuccess { (visits, names) ->
                _uiState.update {
                    it.copy(
                        visits = visits,
                        childNames = names,
                        today = LocalDate.now(),
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat riwayat presensi"))
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PresensiListViewModel(
                    presensiRepository = ServiceLocator.presensiRepository,
                    childRepository = ServiceLocator.childRepository,
                )
            }
        }
    }
}
