package com.antoniusindra.sipatokkong.ui.notifications

/**
 * List surgery on the inbox: sweeping it read, putting a failed sweep back, and stitching pages.
 *
 * Separate from [NotifDisplay], which answers what a single row shows and where tapping it goes.
 * These are different questions with different failure modes — the ones here are about a
 * *collection*, and every one of them can go wrong in a way the user sees: a row that moves, a row
 * that appears twice, a row that comes back from the dead after a failed write.
 *
 * **Nothing here may reorder, add or remove a row except where that is the entire point.** Marking
 * read flips a flag in place; it never re-sorts. That is not an implementation detail, it is the
 * reason the inbox is one list rather than read/unread tabs: under tabs the row you just tapped
 * would leave the tab you were looking at.
 *
 * Free of Compose and Supabase types so the rules can be unit-tested directly, the same way
 * [com.antoniusindra.sipatokkong.ui.feedback.FeedbackTriage] is. The ViewModel keeps only
 * orchestration, because with no coroutines-test in this suite it cannot be tested at all.
 */
object NotifInbox {

    /** The rows a sweep would touch. Captured *before* the optimistic write, to revert by later. */
    fun unreadIds(items: List<NotificationItem>): Set<String> =
        items.filterNot { it.isRead }.map { it.id }.toSet()

    /** Every row read. Positional `map`: same rows, same order, same size. */
    fun allRead(items: List<NotificationItem>): List<NotificationItem> =
        items.map { if (it.isRead) it else it.copy(isRead = true) }

    /**
     * Puts back exactly the rows a failed sweep flipped, and nothing else.
     *
     * Deliberately **not** a snapshot restore. A `refresh()` or a single `markRead` can land while
     * the sweep is in flight, and reinstating a whole earlier list would resurrect rows that are
     * gone and undo a mark that already succeeded. Working from the captured id set leaves every
     * other row at whatever the newer information says, and an id that has since disappeared is
     * simply not found — not re-added.
     */
    fun revertToUnread(
        items: List<NotificationItem>,
        ids: Set<String>,
    ): List<NotificationItem> =
        items.map { if (it.id in ids) it.copy(isRead = false) else it }

    /**
     * Appends an older page beneath what is already held, dropping anything already present.
     *
     * **The dedupe is load-bearing, not tidiness.** The list is rendered with `key = { it.id }`, and
     * `LazyColumn` throws on a duplicate key — so a notification arriving between two page loads
     * would take the screen down. Offset paging makes that reachable: a row inserted above the
     * window shifts the server's page boundary, and the boundary row comes back twice.
     *
     * The copy already held wins. It is the newer one as far as this device is concerned — it may
     * carry an optimistic read flag the server had not yet stored when the page was fetched, and
     * letting the stale copy win would flip a row back to unread under the user's eyes.
     *
     * [forceRead] applies after a successful sweep: the server marked *every* row read, including
     * ones no page had loaded, so a page fetched afterwards must not arrive claiming otherwise.
     */
    fun appendPage(
        existing: List<NotificationItem>,
        incoming: List<NotificationItem>,
        forceRead: Boolean = false,
    ): List<NotificationItem> {
        val held = existing.map { it.id }.toSet()
        val fresh = incoming
            .filterNot { it.id in held }
            .map { if (forceRead && !it.isRead) it.copy(isRead = true) else it }
        return existing + fresh
    }

    /**
     * Whether to offer "Muat lebih banyak".
     *
     * A full page means the server had at least that many, so there may be older rows. Cheaper than
     * asking for an exact count on every inbox open; the cost is one wasted request when the total
     * happens to be an exact multiple of the page size, and that tap removes the button itself.
     */
    fun hasMore(received: Int, pageSize: Int): Boolean = received >= pageSize
}
