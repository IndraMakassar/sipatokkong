package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.ui.notifications.NotifInbox
import com.antoniusindra.sipatokkong.ui.notifications.NotifTarget
import com.antoniusindra.sipatokkong.ui.notifications.NotifType
import com.antoniusindra.sipatokkong.ui.notifications.NotificationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to the inbox as a *list* — swept read, put back after a failed sweep, and paged.
 *
 * The invariant underneath all of it: **a row never moves.** That is what makes the inbox one list
 * instead of read/unread tabs, and the reason is behavioural rather than aesthetic — marking read
 * happens on tap, so under tabs the row being touched would leave the tab being looked at. Two
 * tests below assert order and size explicitly so that decision cannot be undone by accident.
 *
 * The other two traps are less obvious and both were reachable. A failed bulk write cannot be undone
 * by restoring an earlier snapshot, because a refresh or a single mark can land while it is in
 * flight — so the revert works from captured ids instead. And offset paging can hand back the same
 * row twice when something is inserted above the window, which `LazyColumn` answers by throwing on
 * the duplicate key rather than by drawing it twice.
 */
class NotificationInboxTest {

    private fun item(id: String, isRead: Boolean = false) = NotificationItem(
        id = id,
        type = NotifType.FEEDBACK_BALASAN,
        title = "Kak Dewi · Andi",
        body = "Terima kasih, Bu.",
        createdAt = "2026-08-30T08:00:00Z",
        isRead = isRead,
        target = NotifTarget.FeedbackThread("fb-$id"),
    )

    // ---- marking everything read ----

    @Test
    fun `every unread row flips and the read ones are left alone`() {
        val before = listOf(item("a"), item("b", isRead = true), item("c"))
        val after = NotifInbox.allRead(before)
        assertTrue(after.all { it.isRead })
        // The already-read row is returned as the very same object, not a needless copy.
        assertEquals(before[1], after[1])
    }

    @Test
    fun `a sweep never reorders, drops or adds a row`() {
        // The guard on the whole "one list, not two tabs" decision. If marking read ever starts
        // moving rows, the reason for rejecting tabs evaporates and this fails first.
        val before = listOf(item("a"), item("b", isRead = true), item("c"), item("d"))
        val after = NotifInbox.allRead(before)
        assertEquals(before.size, after.size)
        assertEquals(before.map { it.id }, after.map { it.id })
    }

    @Test
    fun `an inbox with nothing unread comes back unchanged`() {
        // What makes the ViewModel's early return provable: there is genuinely nothing to write.
        val before = listOf(item("a", isRead = true), item("b", isRead = true))
        assertEquals(before, NotifInbox.allRead(before))
        assertTrue(NotifInbox.unreadIds(before).isEmpty())
    }

    @Test
    fun `unreadIds names exactly the rows a sweep would touch`() {
        val items = listOf(item("a"), item("b", isRead = true), item("c"))
        assertEquals(setOf("a", "c"), NotifInbox.unreadIds(items))
    }

    // ---- reverting a failed sweep ----

    @Test
    fun `a failed sweep puts back exactly what it flipped`() {
        val before = listOf(item("a"), item("b", isRead = true), item("c"))
        val flipped = NotifInbox.unreadIds(before)
        val swept = NotifInbox.allRead(before)
        assertEquals(before, NotifInbox.revertToUnread(swept, flipped))
    }

    @Test
    fun `a row already read before the sweep is not made unread by the revert`() {
        // The case a snapshot restore gets wrong in the other direction, and the reason the revert
        // works from ids: row b was read before anyone pressed anything and must stay read.
        val before = listOf(item("a"), item("b", isRead = true))
        val flipped = NotifInbox.unreadIds(before)   // only "a"
        val reverted = NotifInbox.revertToUnread(NotifInbox.allRead(before), flipped)
        assertFalse(reverted.first { it.id == "a" }.isRead)
        assertTrue(reverted.first { it.id == "b" }.isRead)
    }

    @Test
    fun `an id that is no longer in the list is ignored, not resurrected`() {
        // A refresh landed while the sweep was in flight and row "gone" is no longer returned.
        val current = listOf(item("a", isRead = true))
        val reverted = NotifInbox.revertToUnread(current, setOf("a", "gone"))
        assertEquals(listOf("a"), reverted.map { it.id })
    }

    @Test
    fun `a row that arrived after the sweep started keeps what the server said`() {
        val current = listOf(item("a", isRead = true), item("new", isRead = false))
        val reverted = NotifInbox.revertToUnread(current, setOf("a"))
        assertFalse(reverted.first { it.id == "new" }.isRead)
    }

    @Test
    fun `a revert preserves order and size`() {
        val current = listOf(item("a", isRead = true), item("b", isRead = true), item("c", isRead = true))
        val reverted = NotifInbox.revertToUnread(current, setOf("a", "c"))
        assertEquals(listOf("a", "b", "c"), reverted.map { it.id })
        assertEquals(3, reverted.size)
    }

    // ---- paging ----

    @Test
    fun `a full page means there may be more`() {
        assertTrue(NotifInbox.hasMore(received = 50, pageSize = 50))
    }

    @Test
    fun `a short page is the end`() {
        assertFalse(NotifInbox.hasMore(received = 49, pageSize = 50))
        assertFalse(NotifInbox.hasMore(received = 0, pageSize = 50))
    }

    @Test
    fun `the older page lands after the newer one, in order`() {
        val held = listOf(item("a"), item("b"))
        val next = listOf(item("c"), item("d"))
        assertEquals(listOf("a", "b", "c", "d"), NotifInbox.appendPage(held, next).map { it.id })
    }

    @Test
    fun `a row that arrives on two pages is kept once`() {
        // Reachable with offset paging: a notification inserted above the window shifts the server's
        // page boundary and the boundary row comes back again. LazyColumn is keyed by id and THROWS
        // on a duplicate, so without this the screen crashes rather than showing the row twice.
        val held = listOf(item("a"), item("b"))
        val overlapping = listOf(item("b"), item("c"))
        assertEquals(listOf("a", "b", "c"), NotifInbox.appendPage(held, overlapping).map { it.id })
    }

    @Test
    fun `the copy already held wins, so an optimistic mark survives a stale page`() {
        // "b" was tapped and marked read locally; the page was fetched before that write landed, so
        // the server's copy still says unread. Letting it win would flip the row back under the
        // user's eyes.
        val held = listOf(item("b", isRead = true))
        val stale = listOf(item("b", isRead = false), item("c"))
        val merged = NotifInbox.appendPage(held, stale)
        assertTrue(merged.first { it.id == "b" }.isRead)
        assertEquals(listOf("b", "c"), merged.map { it.id })
    }

    @Test
    fun `an empty page changes nothing`() {
        val held = listOf(item("a"), item("b"))
        assertEquals(held, NotifInbox.appendPage(held, emptyList()))
    }

    @Test
    fun `after a successful sweep an appended page arrives already read`() {
        // The sweep marked every row the server holds, including ones no page had loaded yet. A page
        // fetched afterwards must not come back claiming rows are new when the database disagrees.
        val held = listOf(item("a", isRead = true))
        val older = listOf(item("b", isRead = false), item("c", isRead = false))
        val merged = NotifInbox.appendPage(held, older, forceRead = true)
        assertTrue(merged.all { it.isRead })
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }
}
