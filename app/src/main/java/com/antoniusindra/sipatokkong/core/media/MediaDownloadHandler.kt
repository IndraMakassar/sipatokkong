package com.antoniusindra.sipatokkong.core.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.antoniusindra.sipatokkong.core.util.ErrorText

/** A download waiting for the user to accept its data cost. */
data class PendingDownload(val ref: MediaRef, val sizeBytes: Long) {
    val sizeLabel: String get() = MediaSize.format(sizeBytes)
}

data class MediaDownloadState(
    /** Looking up the size before asking. Distinct from [progress]: nothing is downloading yet. */
    val isPreparing: Boolean = false,
    /** 0f..1f while downloading, null when idle. */
    val progress: Float? = null,
    val pending: PendingDownload? = null,
    /** Success text, e.g. "Tersimpan di Galeri". */
    val message: String? = null,
    /** Uri of the saved file, for an "Buka" action. */
    val savedUri: String? = null,
    /**
     * A failed download. Deliberately separate from any screen's `error` field — a download that
     * fails must never replace the content the user was looking at.
     */
    val error: String? = null,
) {
    val isBusy: Boolean get() = isPreparing || progress != null
}

/**
 * The download state machine, shared by every screen that shows media.
 *
 * Composed into ViewModels rather than duplicated in each one: three screens need identical
 * behaviour, and the alternative was the same sixty lines written three times.
 */
class MediaDownloadHandler(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(MediaDownloadState())
    val state: StateFlow<MediaDownloadState> = _state.asStateFlow()

    /** Tapped download. Photos start immediately; videos ask first, with their size. */
    fun request(ref: MediaRef) {
        if (_state.value.isBusy) return

        if (!ref.needsConfirmation) {
            start(ref)
            return
        }

        scope.launch {
            _state.update { it.copy(isPreparing = true, error = null, message = null) }
            runCatching { MediaDownloader.sizeOf(ref) }
                .onSuccess { size ->
                    _state.update {
                        it.copy(isPreparing = false, pending = PendingDownload(ref, size))
                    }
                }
                .onFailure {
                    // Not knowing the size is no reason to block the download; ask without it.
                    _state.update {
                        it.copy(isPreparing = false, pending = PendingDownload(ref, -1L))
                    }
                }
        }
    }

    /** The user accepted the size shown in the dialog. */
    fun confirm() {
        val ref = _state.value.pending?.ref ?: return
        _state.update { it.copy(pending = null) }
        start(ref)
    }

    fun cancel() = _state.update { it.copy(pending = null) }

    fun dismissMessage() = _state.update { it.copy(message = null, savedUri = null, error = null) }

    private fun start(ref: MediaRef) {
        scope.launch {
            _state.update { it.copy(progress = 0f, error = null, message = null, savedUri = null) }
            runCatching {
                MediaDownloader.download(ref) { fraction ->
                    _state.update { it.copy(progress = fraction) }
                }
            }.onSuccess { uri ->
                _state.update {
                    it.copy(
                        progress = null,
                        message = "Tersimpan di Galeri",
                        savedUri = uri.toString(),
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(progress = null, error = ErrorText.of(e, "Gagal mengunduh media"))
                }
            }
        }
    }
}
