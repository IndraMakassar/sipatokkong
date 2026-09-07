package com.antoniusindra.sipatokkong.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.util.ErrorText
import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.data.repository.PresensiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RelawanKunjunganUiState(
    val visits: List<Presensi> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class RelawanKunjunganViewModel(
    private val relawanId: String,
    private val from: LocalDate,
    private val to: LocalDate,
    private val presensiRepository: PresensiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RelawanKunjunganUiState())
    val uiState: StateFlow<RelawanKunjunganUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                presensiRepository.listForRelawan(relawanId, from, to)
            }.onSuccess { list ->
                _uiState.update { it.copy(visits = list, isLoading = false) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = ErrorText.of(e, "Gagal memuat detail kunjungan"))
                }
            }
        }
    }

    suspend fun getSignedUrl(path: String): String = presensiRepository.signedFotoUrl(path)

    class Factory(
        private val relawanId: String,
        private val from: LocalDate,
        private val to: LocalDate,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return RelawanKunjunganViewModel(
                relawanId = relawanId,
                from = from,
                to = to,
                presensiRepository = ServiceLocator.presensiRepository,
            ) as T
        }
    }
}
