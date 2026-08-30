package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.PresensiRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One volunteer's attendance within the selected range. */
data class RekapRelawan(
    val id: String,
    val nama: String,
    val isActive: Boolean,
    val jumlahKunjungan: Int,
    val jumlahAnak: Int,
    val kunjunganTerakhir: LocalDate?,
)

data class RekapAbsensiUiState(
    val visits: List<Presensi> = emptyList(),
    val relawan: List<Profile> = emptyList(),
    val from: LocalDate = LocalDate.now().minusDays(29),
    val to: LocalDate = LocalDate.now(),
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    /**
     * Every volunteer, with their counts for the range.
     *
     * Built from the volunteer list rather than from the presensi rows on purpose: a volunteer who
     * visited nobody has no rows at all, and they are exactly the entry an attendance overview
     * exists to surface. Grouping the rows instead would make them disappear.
     *
     * Rows are filtered to the range here as well as in the query, so the derived numbers stay
     * correct no matter what the caller loaded.
     */
    val perRelawan: List<RekapRelawan>
        get() {
            val inRange = visits.filter { visit ->
                val d = visit.parsedDate ?: return@filter false
                !d.isBefore(from) && !d.isAfter(to)
            }
            val byRelawan = inRange.groupBy { it.relawanId }

            return relawan.map { profile ->
                val rows = byRelawan[profile.id].orEmpty()
                RekapRelawan(
                    id = profile.id,
                    nama = profile.fullName,
                    isActive = profile.isActive,
                    jumlahKunjungan = rows.size,
                    jumlahAnak = rows.map { it.anakId }.distinct().size,
                    kunjunganTerakhir = rows.mapNotNull { it.parsedDate }.maxOrNull(),
                )
            }.sortedWith(
                // Busiest first — the recap is read to compare volunteers, and a name-sorted list
                // buries the number it is being read for.
                compareByDescending<RekapRelawan> { it.jumlahKunjungan }
                    .thenBy { it.nama.lowercase() }
            )
        }

    /** Name search, case-insensitive. Empty query shows everyone. */
    val visibleRelawan: List<RekapRelawan>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return perRelawan
            return perRelawan.filter { it.nama.contains(q, ignoreCase = true) }
        }

    val totalKunjungan: Int get() = perRelawan.sumOf { it.jumlahKunjungan }

    /** Volunteers with nothing logged in the range — the number the foundation acts on. */
    val relawanTanpaKunjungan: Int get() = perRelawan.count { it.jumlahKunjungan == 0 }

    val isRangeValid: Boolean get() = !to.isBefore(from)
}

/**
 * Admin attendance recap, per volunteer, over a date range.
 *
 * Everything is aggregated client-side from plain row lists, matching the rest of the app — there
 * is no RPC, view or `group by` anywhere in this project, and adding one for a table this small
 * would be the odd one out.
 *
 * No `init { refresh() }`: the screen loads from a `LaunchedEffect`. The older admin ViewModels do
 * both, which costs two round-trips per entry; that pattern was already removed from
 * `ProgramListViewModel` and is not repeated here.
 */
class RekapAbsensiViewModel(
    private val presensiRepository: PresensiRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RekapAbsensiUiState())
    val uiState: StateFlow<RekapAbsensiUiState> = _uiState.asStateFlow()

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun onFromChange(date: LocalDate?) {
        if (date == null) return
        _uiState.update { it.copy(from = date) }
        refresh()
    }

    fun onToChange(date: LocalDate?) {
        if (date == null) return
        _uiState.update { it.copy(to = date) }
        refresh()
    }

    fun refresh() {
        val s = _uiState.value
        if (!s.isRangeValid) {
            _uiState.update {
                it.copy(isLoading = false, error = "Tanggal \"Sampai\" tidak boleh sebelum \"Dari\".")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val visits = presensiRepository.listBetween(s.from, s.to)
                val relawan = profileRepository.profilesByRoleFull(UserRole.RELAWAN.name.lowercase())
                visits to relawan
            }.onSuccess { (visits, relawan) ->
                _uiState.update { it.copy(visits = visits, relawan = relawan, isLoading = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat rekap absensi"))
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RekapAbsensiViewModel(
                    presensiRepository = ServiceLocator.presensiRepository,
                    profileRepository = ServiceLocator.profileRepository,
                )
            }
        }
    }
}
