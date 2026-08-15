package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Child
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageChildrenUiState(
    val children: List<Child> = emptyList(),
    /** orangtua_id -> nama orang tua, for the subtitle on each row. */
    val parentNames: Map<String, String> = emptyMap(),
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val visibleChildren: List<Child>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return children
            return children.filter { child ->
                child.nama.contains(q, ignoreCase = true) ||
                    parentNames[child.orangtuaId]?.contains(q, ignoreCase = true) == true
            }
        }

    fun parentNameOf(child: Child): String = parentNames[child.orangtuaId] ?: "Orang tua tidak dikenal"
}

class ManageChildrenViewModel(
    private val childRepository: ChildRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageChildrenUiState())
    val uiState: StateFlow<ManageChildrenUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                // profilesByRoleFull (not profilesByRole) so deactivated parents still resolve to a name.
                val children = childRepository.allChildren()
                val parents = profileRepository.profilesByRoleFull("orangtua")
                children to parents.associate { it.id to it.fullName }
            }.onSuccess { (children, names) ->
                _uiState.update {
                    it.copy(children = children, parentNames = names, isLoading = false)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Gagal memuat data anak")
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ManageChildrenViewModel(
                    childRepository = ServiceLocator.childRepository,
                    profileRepository = ServiceLocator.profileRepository,
                )
            }
        }
    }
}
