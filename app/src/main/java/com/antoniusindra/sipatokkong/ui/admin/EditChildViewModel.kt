package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.Child
import com.antoniusindra.sipatokkong.data.model.ChildUpdate
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.ProfileOption
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class EditChildUiState(
    val child: Child? = null,
    /** Active parents — the only valid targets when moving a child. */
    val parentOptions: List<ProfileOption> = emptyList(),
    /** Active volunteers — the only ones offered for a new assignment. */
    val relawanOptions: List<ProfileOption> = emptyList(),
    /** id -> name for every profile, so inactive parents/volunteers still render with a name. */
    val allNames: Map<String, String> = emptyMap(),
    val selectedOrangtuaId: String? = null,
    val nama: String = "",
    val tanggalLahir: LocalDate? = null,
    val catatan: String = "",
    val isActive: Boolean = true,
    /** Staged assignments; diffed against [originalAssignedIds] on save. */
    val assignedIds: List<String> = emptyList(),
    val originalAssignedIds: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
) {
    val selectedParentName: String? get() = selectedOrangtuaId?.let { allNames[it] }

    fun nameOf(id: String): String = allNames[id] ?: "Relawan tidak dikenal"

    /** Volunteers that can still be added — active ones not already staged. */
    val assignableRelawan: List<ProfileOption>
        get() = relawanOptions.filter { it.id !in assignedIds }
}

class EditChildViewModel(
    private val anakId: String,
    private val childRepository: ChildRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditChildUiState())
    val uiState: StateFlow<EditChildUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val child = childRepository.getChild(anakId)
                val allParents = profileRepository.profilesByRoleFull("orangtua")
                val allRelawan = profileRepository.profilesByRoleFull("relawan")
                val assigned = childRepository.assignedVolunteerIds(anakId)
                LoadResult(child, allParents, allRelawan, assigned)
            }.onSuccess { result ->
                val child = result.child
                if (child == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Data anak tidak ditemukan.") }
                    return@onSuccess
                }
                val names = (result.parents + result.relawan).associate { it.id to it.fullName }
                _uiState.update {
                    it.copy(
                        child = child,
                        parentOptions = result.parents
                            .filter { p -> p.isActive }
                            .map { p -> ProfileOption(p.id, p.fullName) },
                        relawanOptions = result.relawan
                            .filter { r -> r.isActive }
                            .map { r -> ProfileOption(r.id, r.fullName) },
                        allNames = names,
                        selectedOrangtuaId = child.orangtuaId,
                        nama = child.nama,
                        tanggalLahir = child.tanggalLahir?.let { raw ->
                            runCatching { LocalDate.parse(raw) }.getOrNull()
                        },
                        catatan = child.catatanKondisi.orEmpty(),
                        isActive = child.isActive,
                        assignedIds = result.assigned,
                        originalAssignedIds = result.assigned,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat data anak"))
                }
            }
        }
    }

    private data class LoadResult(
        val child: Child?,
        val parents: List<Profile>,
        val relawan: List<Profile>,
        val assigned: List<String>,
    )

    fun onNamaChange(v: String) = _uiState.update { it.copy(nama = v, error = null) }
    fun onTanggalLahirChange(d: LocalDate?) = _uiState.update { it.copy(tanggalLahir = d) }
    fun onCatatanChange(v: String) = _uiState.update { it.copy(catatan = v) }
    fun onActiveChange(v: Boolean) = _uiState.update { it.copy(isActive = v) }
    fun onOrangtuaSelected(id: String) = _uiState.update { it.copy(selectedOrangtuaId = id, error = null) }

    fun addRelawan(id: String) = _uiState.update {
        if (id in it.assignedIds) it else it.copy(assignedIds = it.assignedIds + id, error = null)
    }

    fun removeRelawan(id: String) = _uiState.update {
        it.copy(assignedIds = it.assignedIds - id, error = null)
    }

    fun save() {
        val s = _uiState.value
        val child = s.child ?: return
        val targetParentId = s.selectedOrangtuaId
        if (targetParentId == null) {
            _uiState.update { it.copy(error = "Pilih orang tua terlebih dahulu.") }
            return
        }
        if (s.nama.isBlank()) {
            _uiState.update { it.copy(error = "Nama anak wajib diisi.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            try {
                // Same soft "one child per parent" rule as CreateChildViewModel, but only when the
                // parent actually changes — hasChild() would otherwise match this very child.
                if (targetParentId != child.orangtuaId && childRepository.hasChild(targetParentId)) {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = "Orang tua tujuan sudah memiliki data anak. Untuk saat ini, satu orang tua hanya bisa memiliki satu anak.",
                        )
                    }
                    return@launch
                }

                childRepository.updateChild(
                    id = anakId,
                    update = ChildUpdate(
                        nama = s.nama.trim(),
                        tanggalLahir = s.tanggalLahir?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        orangtuaId = targetParentId,
                        catatanKondisi = s.catatan.ifBlank { null },
                        isActive = s.isActive,
                    ),
                )

                // Apply the staged assignment diff.
                (s.assignedIds - s.originalAssignedIds.toSet()).forEach { id ->
                    childRepository.assignVolunteer(anakId, id)
                }
                (s.originalAssignedIds - s.assignedIds.toSet()).forEach { id ->
                    childRepository.unassignVolunteer(anakId, id)
                }

                _uiState.update { it.copy(isSubmitting = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = ErrorText.of(e, "Gagal menyimpan perubahan"))
                }
            }
        }
    }

    companion object {
        fun factory(anakId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                EditChildViewModel(
                    anakId = anakId,
                    childRepository = ServiceLocator.childRepository,
                    profileRepository = ServiceLocator.profileRepository,
                )
            }
        }
    }
}
