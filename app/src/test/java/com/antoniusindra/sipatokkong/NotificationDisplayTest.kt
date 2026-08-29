package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.util.formatIsoDate
import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.data.model.Notification
import com.antoniusindra.sipatokkong.ui.notifications.NotifTarget
import com.antoniusindra.sipatokkong.ui.notifications.NotifDisplay
import com.antoniusindra.sipatokkong.ui.notifications.NotifType
import com.antoniusindra.sipatokkong.ui.notifications.NotificationItem
import com.antoniusindra.sipatokkong.ui.notifications.NotificationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What a notification row shows, and where tapping it goes.
 *
 * The trap in this table is that the client never writes to it: every row arrives from a database
 * trigger the app cannot see. The Postgres enum declares six kinds and nothing writes three of
 * them; the three that are written split by role, so neither a parent nor a volunteer ever sees the
 * full set. `entity_table`/`entity_id` are an unconstrained polymorphic pointer with no foreign key
 * behind them. `on_balasan_insert` deliberately stores the feedback **thread** id rather than the
 * reply id. And `on_feedback_insert` freezes a copy of the parent's note into `body`, filler and
 * all. Every one of those is a way for the inbox to render a useless card, drop a row someone
 * needed, or navigate to a screen that can only fail.
 */
class NotificationDisplayTest {

    private fun notif(
        id: String = "n-1",
        type: String = "tugas_baru",
        title: String? = "Kak Dewi memberi tugas untuk Andi",
        body: String? = "Latihan Sensori",
        entityTable: String? = "tugas",
        entityId: String? = "t-1",
        isRead: Boolean = false,
        createdAt: String? = "2026-08-29T08:00:00Z",
    ) = Notification(
        id = id,
        recipientId = "p-1",
        type = type,
        title = title,
        body = body,
        entityTable = entityTable,
        entityId = entityId,
        isRead = isRead,
        createdAt = createdAt,
    )

    private fun uiItem(id: String, isRead: Boolean) = NotificationItem(
        id = id,
        type = NotifType.TUGAS_BARU,
        title = "Kak Dewi memberi tugas untuk Andi",
        body = "Latihan Sensori",
        createdAt = "2026-08-29T08:00:00Z",
        isRead = isRead,
        target = NotifTarget.None,
    )

    // ---- kindOf ----

    @Test
    fun `a new task is a task`() {
        assertEquals(NotifType.TUGAS_BARU, NotifDisplay.kindOf("tugas_baru"))
    }

    @Test
    fun `a volunteer reply is a reply`() {
        assertEquals(NotifType.FEEDBACK_BALASAN, NotifDisplay.kindOf("feedback_balasan"))
    }

    @Test
    fun `the three kinds nothing writes still render, they do not vanish`() {
        // Declared in notif_type but produced by no trigger and no cron job today. A cron job could
        // start writing them without an app release, so they must degrade, not disappear.
        for (type in listOf("tugas_reminder", "tugas_missed", "presensi")) {
            assertEquals(type, NotifType.LAINNYA, NotifDisplay.kindOf(type))
        }
    }

    @Test
    fun `an unknown future kind renders too`() {
        // The whole reason Notification.type is a String: an unknown enum constant would throw
        // inside decodeList and take down the entire inbox, not one card.
        assertEquals(NotifType.LAINNYA, NotifDisplay.kindOf("kabar_gembira"))
        assertEquals(NotifType.LAINNYA, NotifDisplay.kindOf(""))
        assertEquals(NotifType.LAINNYA, NotifDisplay.kindOf(null))
    }

    @Test
    fun `a parent's new note is the volunteer's one kind`() {
        // on_feedback_insert is the only trigger that ever addresses a relawan, so this single
        // value is a volunteer's entire inbox. Mapping it to LAINNYA -- as this test used to
        // assert, back when only a parent had an inbox -- would hand them a list of generic bells.
        assertEquals(NotifType.FEEDBACK_BARU, NotifDisplay.kindOf("feedback_baru"))
    }

    // ---- targetOf ----

    @Test
    fun `a feedback notification opens its thread`() {
        assertEquals(
            NotifTarget.FeedbackThread("fb-1"),
            NotifDisplay.targetOf("feedback", "fb-1"),
        )
    }

    @Test
    fun `a task notification opens nothing`() {
        // Not an oversight. The route is tugas_detail/{tugasId}/{occurrenceDate} and a notification
        // carries no occurrence date; a recurring program has dozens and none of them is the right
        // one. Anyone "fixing" the dead tap by navigating will fail here first.
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("tugas", "t-1"))
    }

    @Test
    fun `a feedback notification with no entity id opens nothing`() {
        // The alternative is navigating to feedback_thread/null — a screen that can only fail.
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("feedback", null))
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("feedback", "   "))
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("feedback", ""))
    }

    @Test
    fun `an unknown entity table opens nothing`() {
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("dokumentasi", "d-1"))
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("presensi", "p-1"))
    }

    @Test
    fun `a missing entity table opens nothing`() {
        assertEquals(NotifTarget.None, NotifDisplay.targetOf(null, "x"))
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("", "x"))
    }

    @Test
    fun `the destination comes from entity_table, not from type`() {
        // The icon kind and the destination are independent readings of the same row. Deriving one
        // from the other looks tidier and is wrong.
        val taskKindPointingAtFeedback =
            notif(type = "tugas_baru", entityTable = "feedback", entityId = "fb-9")
        assertEquals(
            NotifTarget.FeedbackThread("fb-9"),
            NotifDisplay.from(taskKindPointingAtFeedback).target,
        )

        val replyKindPointingAtTask =
            notif(type = "feedback_balasan", entityTable = "tugas", entityId = "t-9")
        assertEquals(NotifTarget.None, NotifDisplay.from(replyKindPointingAtTask).target)
    }

    // ---- from ----

    @Test
    fun `a real trigger row maps straight through`() {
        // The literal row on_tugas_insert writes.
        val mapped = NotifDisplay.from(
            notif(
                type = "tugas_baru",
                title = "Kak Dewi memberi tugas untuk Andi",
                body = "Latihan Sensori",
                entityTable = "tugas",
                entityId = "t-1",
                isRead = false,
            )
        )
        assertEquals(NotifType.TUGAS_BARU, mapped.type)
        assertEquals("Kak Dewi memberi tugas untuk Andi", mapped.title)
        assertEquals("Latihan Sensori", mapped.body)
        assertEquals(NotifTarget.None, mapped.target)
        assertEquals(false, mapped.isRead)
    }

    @Test
    fun `a reply notification points at the thread, not the reply`() {
        // on_balasan_insert stores fb.id on purpose so this navigation works. Asserting it here is
        // what stops someone "correcting" the trigger to new.id later.
        val mapped = NotifDisplay.from(
            notif(
                type = "feedback_balasan",
                title = "Kak Dewi · Andi",
                body = "Bagus, lanjutkan latihan memegang sendoknya ya, Bu.",
                entityTable = "feedback",
                entityId = "fb-thread-1",
            )
        )
        assertEquals(NotifType.FEEDBACK_BALASAN, mapped.type)
        assertEquals(NotifTarget.FeedbackThread("fb-thread-1"), mapped.target)
    }

    @Test
    fun `a row with no title still says something, and no body says nothing`() {
        // The two nullable columns are handled differently on purpose: a card with no title is not
        // a card, so the title is backfilled -- but a body is an optional second line, and
        // inventing one for a row that said nothing only adds a line with no information in it.
        val blank = NotifDisplay.from(notif(title = null, body = null))
        assertTrue(blank.title.isNotBlank())
        assertNull(blank.body)

        val whitespace = NotifDisplay.from(notif(title = "   ", body = "  "))
        assertTrue(whitespace.title.isNotBlank())
        assertNull(whitespace.body)
    }

    @Test
    fun `the timestamp is carried, not formatted`() {
        // Formatting belongs to formatIsoDate at render time — FeedbackDisplayTest already owns
        // those cases. Mapping stays clock-free so a label cannot go stale after midnight.
        assertEquals("2026-08-29T08:00:00Z", NotifDisplay.from(notif()).createdAt)

        // And an unparseable stored value must not be able to take the inbox down.
        val broken = NotifDisplay.from(notif(createdAt = "bukan tanggal"))
        assertEquals("bukan tanggal", broken.createdAt)
        assertNull(formatIsoDate(broken.createdAt, LocalDate.of(2026, 8, 29)))
    }

    // ---- from: the row a volunteer gets ----

    @Test
    fun `the row on_feedback_insert writes maps straight through`() {
        // The literal that trigger writes, fanned out over anak_relawan. This is the only test that
        // proves a volunteer's inbox is navigable at all -- and the one that catches anyone later
        // pointing entity_id at something other than the thread.
        val mapped = NotifDisplay.from(
            notif(
                type = "feedback_baru",
                title = "Sari Wulandari · Andi",
                body = "Andi rewel sejak pagi.",
                entityTable = "feedback",
                entityId = "fb-1",
            )
        )
        assertEquals(NotifType.FEEDBACK_BARU, mapped.type)
        assertEquals("Sari Wulandari · Andi", mapped.title)
        assertEquals("Andi rewel sejak pagi.", mapped.body)
        assertEquals(NotifTarget.FeedbackThread("fb-1"), mapped.target)
    }

    @Test
    fun `a feedback_baru row with a null title still names itself`() {
        // The blank-title fallback path, which is why this fixture keeps the OLD generic wording
        // while the trigger fixtures below use the new name-bearing format. Since migration 0010
        // the trigger writes "<pengirim> · <anak>" from columns, so this string is a genuine last
        // resort rather than a mirror of it -- and it is nearly unreachable, because both source
        // columns are `not null` and the trigger coalesces anyway.
        val mapped = NotifDisplay.from(notif(type = "feedback_baru", title = null))
        assertEquals("Feedback baru dari orang tua", mapped.title)
    }

    @Test
    fun `a feedback_baru row with no entity id opens nothing`() {
        // A volunteer must not be able to reach feedback_thread/null either.
        val mapped = NotifDisplay.from(
            notif(type = "feedback_baru", entityTable = "feedback", entityId = null)
        )
        assertEquals(NotifTarget.None, mapped.target)
    }

    // ---- the filler note ----

    @Test
    fun `the filler note is dropped, not repeated down the list`() {
        // feedback.isi is `not null` while the form's "Catatan Tambahan" is optional, so
        // CreateFeedbackViewModel backfills it with CATATAN_RUTIN -- and mood-plus-chips with no
        // typing is the quickest way to use that form. on_feedback_insert then freezes
        // left(isi, 120) into body, so rendering it literally gives a volunteer one identical
        // sentence repeated down their entire inbox.
        val mapped = NotifDisplay.from(notif(type = "feedback_baru", body = CATATAN_RUTIN))
        assertNull(mapped.body)
    }

    @Test
    fun `a real note is kept verbatim`() {
        val mapped = NotifDisplay.from(
            notif(type = "feedback_baru", body = "Andi menolak latihan sore ini.")
        )
        assertEquals("Andi menolak latihan sore ini.", mapped.body)
    }

    @Test
    fun `whitespace around the filler still counts as filler`() {
        // Pins the order: trim first, then compare. The other way round lets a stray space through.
        assertNull(NotifDisplay.from(notif(body = "  $CATATAN_RUTIN  ")).body)
        assertNull(NotifDisplay.from(notif(body = "   ")).body)
        assertNull(NotifDisplay.from(notif(body = null)).body)
    }

    @Test
    fun `a note that merely starts with the filler is kept`() {
        // Equality, not startsWith: a parent can type that sentence themselves and then add to it,
        // and what they added is the part worth reading. Truncation cannot fake a match either --
        // the constant is 32 characters and the trigger cuts at 120.
        val extended = "$CATATAN_RUTIN Tapi hari ini beda."
        assertEquals(extended, NotifDisplay.from(notif(body = extended)).body)
    }

    // ---- unreadCount and badgeLabel ----

    @Test
    fun `the count is the unread rows, nothing else`() {
        val state = NotificationUiState(
            items = listOf(
                uiItem("a", isRead = false),
                uiItem("b", isRead = true),
                uiItem("c", isRead = false),
                uiItem("d", isRead = true),
                uiItem("e", isRead = true),
            )
        )
        assertEquals(2, state.unreadCount)
        assertEquals(0, NotificationUiState().unreadCount)
        assertEquals(0, NotificationUiState(items = listOf(uiItem("a", true))).unreadCount)
    }

    @Test
    fun `nothing unread means no badge at all`() {
        // Null, not "0": a zero badge is a permanent mark on the bell that means nothing.
        assertNull(NotifDisplay.badgeLabel(0))
        assertNull(NotifDisplay.badgeLabel(-1))
    }

    @Test
    fun `past nine the badge stops counting`() {
        assertEquals("1", NotifDisplay.badgeLabel(1))
        assertEquals("9", NotifDisplay.badgeLabel(9))
        assertEquals("9+", NotifDisplay.badgeLabel(10))
        assertEquals("9+", NotifDisplay.badgeLabel(250))
    }
}
