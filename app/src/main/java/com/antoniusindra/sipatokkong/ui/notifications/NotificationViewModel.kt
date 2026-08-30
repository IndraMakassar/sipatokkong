package com.antoniusindra.sipatokkong.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.push.PUSH_LOG_TAG
import com.antoniusindra.sipatokkong.data.repository.NotificationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val items: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = true,

    /** A failed *load*. Replaces the content, because there is nothing to show. */
    val error: String? = null,

    /**
     * A failed *mark-as-read*, kept apart from [error] on purpose.
     *
     * The list is still on screen and still correct, so it stays exactly where it was and this
     * surfaces as a dismissible banner above it. See `ui/components/ActionErrorBanner.kt` for the
     * bug this split exists to prevent -- it has already shipped in this repo once.
     */
    val actionError: String? = null,

    /**
     * A page-sized answer came back, so there may be older rows behind it.
     *
     * Cheaper than asking for an exact count every time the inbox opens. The cost is one wasted
     * request when the total happens to be an exact multiple of the page size — and that tap
     * removes the button itself, so it happens at most once.
     */
    val hasMore: Boolean = false,

    /** "Muat lebih banyak" is in flight. Distinct from [isLoading], which replaces the screen. */
    val isLoadingMore: Boolean = false,

    /**
     * A sweep has succeeded this session, so every row the server holds is read — including ones no
     * page has loaded. Pages appended afterwards are forced read, or one would arrive announcing
     * rows as new that the database already calls read. Cleared by [NotificationViewModel.refresh],
     * which is the only way a genuinely new row enters the list.
     */
    val allReadSwept: Boolean = false,
) {
    /**
     * Unread rows **among those loaded**, which is what the "Tandai semua" action keys off.
     *
     * Not the same number as the home-screen bell: that one counts every unread row the user owns,
     * server-side, while this one can only see the pages fetched so far.
     */
    val unreadCount: Int get() = items.count { !it.isRead }
}

/**
 * A notification inbox, for whichever role is signed in.
 *
 * Reads `public.notifications` through RLS and marks rows read. Nothing here writes a notification:
 * they arrive only from database triggers.
 */
class NotificationViewModel(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    // No init { refresh() }: the screen drives loading via LaunchedEffect, so the list also reloads
    // on re-entry. Doing both would mean two round-trips on every visit.

    /**
     * Loads the first page, discarding any deeper scroll.
     *
     * Resetting is deliberate. This runs from a `LaunchedEffect` on every entry to the screen, so
     * preserving the depth reached last time would make each re-entry cost as much as the deepest
     * scroll of the previous visit — the opposite of why paging was chosen. It also clears
     * [NotificationUiState.allReadSwept], because a fresh query is the only way a genuinely new
     * unread row can enter the list.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { notificationRepository.listForCurrentUser() }
                .onSuccess { rows ->
                    val items = NotifDisplay.from(rows)
                    _uiState.update {
                        it.copy(
                            items = items,
                            isLoading = false,
                            isLoadingMore = false,
                            allReadSwept = false,
                            hasMore = NotifInbox.hasMore(
                                rows.size, NotificationRepository.PAGE_SIZE.toInt(),
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Gagal memuat notifikasi")
                    }
                }
        }
    }

    /**
     * Fetches the page beneath what is already held.
     *
     * The offset comes from `items.size`, not from a page counter. If a notification arrives above
     * the window between two taps the server's page boundary shifts, and offsetting by what is
     * actually held re-requests the boundary row — which `NotifInbox.appendPage` then drops —
     * rather than stepping over a row that would never be seen. A page counter would skip it.
     *
     * On failure `hasMore` deliberately stays true: the button **is** the retry, which is why this
     * uses the dismissible banner rather than the full-screen `ErrorRetry`.
     */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val offset = _uiState.value.items.size.toLong()
            runCatching {
                notificationRepository.listForCurrentUser(
                    limit = NotificationRepository.PAGE_SIZE,
                    offset = offset,
                )
            }
                .onSuccess { rows ->
                    _uiState.update { s ->
                        s.copy(
                            items = NotifInbox.appendPage(
                                existing = s.items,
                                incoming = NotifDisplay.from(rows),
                                forceRead = s.allReadSwept,
                            ),
                            hasMore = NotifInbox.hasMore(
                                rows.size, NotificationRepository.PAGE_SIZE.toInt(),
                            ),
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            actionError = e.message ?: "Gagal memuat notifikasi lama",
                        )
                    }
                }
        }
    }

    /**
     * Marks one notification read: locally first, then on the server.
     *
     * Optimistic because the same tap usually navigates away. Waiting for the round-trip would
     * leave the unread highlight sitting under the reader's finger and then flip it after the
     * screen had already slid off -- on mobile data this app assumes is patchy.
     *
     * On failure the row goes **back** to unread and a banner appears. Reverting matters as much as
     * the banner does: a list that claims a row is read while the database disagrees would be
     * silently corrected by the next refresh, with nothing to explain why.
     */
    fun markRead(id: String) {
        val current = _uiState.value.items.firstOrNull { it.id == id } ?: return
        if (current.isRead) return  // Second tap on a read row costs nothing.

        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(isRead = true) else it })
        }

        // Same lifetime split as markAllRead, for the same reason.
        //
        // The justification used to run: this one survived by accident, because tapping a feedback
        // card navigates forward and leaves the screen on the back stack, while a task
        // notification navigated nowhere and so lost its write to a quick Back. That is now
        // backwards -- every deep-linkable row navigates forward, so nothing is left relying on
        // the accident.
        //
        // The split stays anyway, and is worth more than when it was added. A row whose target is
        // None still does not navigate, and Back from any destination pops this screen and cancels
        // viewModelScope regardless of how the write got started. "It happens to survive today" is
        // exactly the reasoning that let the sweep ship broken.
        val repo = notificationRepository
        val write = ServiceLocator.appScope.async {
            runCatching { repo.markRead(id) }
                .onFailure { Log.w(PUSH_LOG_TAG, "markRead failed: ${it.message}") }
        }

        viewModelScope.launch {
            write.await()
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            items = state.items.map { if (it.id == id) it.copy(isRead = false) else it },
                            actionError = e.message ?: "Gagal menandai notifikasi",
                        )
                    }
                }
        }
    }

    /**
     * Marks every loaded row read, locally first, then the whole inbox on the server.
     *
     * Optimistic for the same reason [markRead] is — an app-bar button that does nothing visible
     * for a round trip reads as broken on the connections this app assumes.
     *
     * The revert cannot be "put the old list back". A [refresh] or a single [markRead] can land
     * while this is in flight, and restoring a snapshot would resurrect rows that are gone and undo
     * a mark that already succeeded. So it captures **the ids this call flipped** and puts back
     * exactly those, leaving every other row at whatever the newer information says. Positional
     * `map` throughout, like [markRead]: nothing is reordered, added or removed, on success or
     * failure.
     *
     * The server call reaches rows the screen has never loaded. That is intended, it is what the
     * confirmation dialog tells the reader, and it is why nothing here claims a count.
     */
    fun markAllRead() {
        val flipped = NotifInbox.unreadIds(_uiState.value.items)
        // Nothing to write, and nothing a failed write could sensibly explain.
        if (flipped.isEmpty()) return

        _uiState.update { it.copy(items = NotifInbox.allRead(it.items)) }

        // The write runs on the app scope; only its outcome is presented on this one.
        //
        // This is the fix for the sweep never reaching the database. The button's whole purpose is
        // "clear the badge and get out", so the very next gesture pops this destination, clears its
        // ViewModelStore and cancels viewModelScope — and a PATCH started there is cancelled
        // mid-flight, often before it reaches the socket. `runCatching` then swallowed the
        // CancellationException, so the failure branch wrote a banner nobody could see.
        //
        // Cancelling the awaiter below does not cancel the awaited job, because its parent is
        // appScope. Screen alive: it reverts and banners as before. Screen gone: the write still
        // lands and there is nobody to apologise to, which is correct.
        //
        // The local `repo` matters — capturing `this` would keep appScope holding the whole
        // ViewModel, and its item list, alive until the request finished.
        val repo = notificationRepository
        val write = ServiceLocator.appScope.async {
            runCatching { repo.markAllRead() }
                .onFailure { Log.w(PUSH_LOG_TAG, "markAllRead failed: ${it.message}") }
        }

        viewModelScope.launch {
            write.await()
                .onSuccess { _uiState.update { it.copy(allReadSwept = true) } }
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            items = NotifInbox.revertToUnread(state.items, flipped),
                            actionError = e.message ?: "Gagal menandai semua notifikasi",
                        )
                    }
                }
        }
    }

    fun dismissActionError() = _uiState.update { it.copy(actionError = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationViewModel(
                    notificationRepository = ServiceLocator.notificationRepository,
                )
            }
        }
    }
}
