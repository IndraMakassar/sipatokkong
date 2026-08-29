package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.push.PushPayload
import com.antoniusindra.sipatokkong.core.push.PushPermission
import com.antoniusindra.sipatokkong.core.push.PushStyle
import com.antoniusindra.sipatokkong.data.model.CATATAN_RUTIN
import com.antoniusindra.sipatokkong.ui.notifications.NotifDisplay
import com.antoniusindra.sipatokkong.ui.notifications.NotifTarget
import com.antoniusindra.sipatokkong.ui.notifications.NotifType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a push says, and where tapping it goes.
 *
 * A push is a copy of a `public.notifications` row that has travelled through Postgres, an Edge
 * Function and Google's servers, arriving as a bare `Map<String, String>` with types stripped, null
 * columns omitted, and no guarantee the sender is the same age as the app receiving it. Nothing in
 * the parsing may throw, because the alternative to a wrong notification is a crashed background
 * service the user never sees.
 *
 * The rule these tests really defend is that **the tray and the inbox cannot disagree**. Both draw
 * the same row, so both must reach the same wording and the same destination — which is why
 * `PushPayload` delegates to `NotifDisplay` instead of reimplementing it, and why the invariant
 * below is asserted rather than assumed.
 *
 * Not covered here, and not coverable: `SipatokkongMessagingService`, `NotificationManagerCompat`,
 * `PendingIntent`, `Build.VERSION.SDK_INT`, `onNewIntent`, and the entire Edge Function. The suite
 * has no Robolectric and no Deno harness — the same honest gap `MediaDownloadRulesTest` names for
 * `MediaStore`. Everything worth getting right was moved here instead.
 */
class PushPayloadTest {

    private fun push(vararg pairs: Pair<String, String>): Map<String, String> = mapOf(*pairs)

    private fun feedbackPush(
        type: String = "feedback_balasan",
        title: String? = "Kak Dewi · Andi",
        body: String? = "Bagus, lanjutkan ya Bu.",
        entityId: String? = "fb-1",
    ): Map<String, String> = buildMap {
        put("notification_id", "n-1")
        put("type", type)
        title?.let { put("title", it) }
        body?.let { put("body", it) }
        put("entity_table", "feedback")
        entityId?.let { put("entity_id", it) }
    }

    // ---- payload parsing ----

    @Test
    fun `a full data map parses every field`() {
        val parsed = PushPayload.parse(feedbackPush())!!
        assertEquals(NotifType.FEEDBACK_BALASAN, parsed.type)
        assertEquals("Kak Dewi · Andi", parsed.title)
        assertEquals("Bagus, lanjutkan ya Bu.", parsed.body)
        assertEquals(NotifTarget.FeedbackThread("fb-1"), parsed.target)
        assertEquals("n-1", parsed.notificationId)
    }

    @Test
    fun `a missing title falls back to the same words the inbox uses`() {
        // The Edge Function omits null columns, so an absent title is normal, not corrupt. It must
        // resolve to exactly what the card would say -- one row worded two ways is a bug a user can
        // see, with the tray and the list open side by side.
        //
        // These two keep the OLD generic wording deliberately: since migration 0010 the triggers
        // write "<pengirim> · <anak>" from columns, so these strings are the last-resort fallbacks
        // rather than a copy of what any live row carries. Fixtures elsewhere in this file use the
        // new format because they stand in for real trigger rows.
        val parsed = PushPayload.parse(feedbackPush(title = null))!!
        assertEquals("Balasan feedback", parsed.title)

        val newNote = PushPayload.parse(feedbackPush(type = "feedback_baru", title = null))!!
        assertEquals("Feedback baru dari orang tua", newNote.title)
    }

    @Test
    fun `an unknown type renders rather than throwing`() {
        // Same contract kindOf has, and the same reason Notification.type is a String: an APK in the
        // field can be older than the function sending to it, and a kind added later must still
        // arrive as something rather than crash a background service.
        val parsed = PushPayload.parse(feedbackPush(type = "kabar_gembira"))!!
        assertEquals(NotifType.LAINNYA, parsed.type)
    }

    @Test
    fun `a missing type is still a notification`() {
        val parsed = PushPayload.parse(push("notification_id" to "n-1", "title" to "Halo"))!!
        assertEquals(NotifType.LAINNYA, parsed.type)
        assertEquals("Halo", parsed.title)
    }

    @Test
    fun `the filler note is dropped in the tray too`() {
        // on_feedback_insert freezes left(isi, 120) into body, and an empty note is backfilled with
        // CATATAN_RUTIN. Shown literally, a volunteer's notification tray repeats one identical
        // sentence -- the same defect already fixed twice in the list. bodyLineOf owns the rule; this
        // asserts the push inherits it rather than quietly bypassing it.
        val parsed = PushPayload.parse(feedbackPush(type = "feedback_baru", body = CATATAN_RUTIN))!!
        assertNull(parsed.body)
    }

    @Test
    fun `a blank body becomes no body`() {
        assertNull(PushPayload.parse(feedbackPush(body = "   "))!!.body)
        assertNull(PushPayload.parse(feedbackPush(body = null))!!.body)
    }

    @Test
    fun `an empty map is nothing to show`() {
        assertNull(PushPayload.parse(emptyMap()))
    }

    @Test
    fun `unrecognised keys are ignored`() {
        // Forward compatibility in the direction that actually happens: the Edge Function is
        // redeployed in seconds, an APK takes a store review. A new field must not break an old app.
        val parsed = PushPayload.parse(feedbackPush() + ("sound_name" to "chime"))!!
        assertEquals("Kak Dewi · Andi", parsed.title)
        assertEquals(NotifTarget.FeedbackThread("fb-1"), parsed.target)
    }

    // ---- route mapping ----

    @Test
    fun `a feedback push opens its thread`() {
        // The literal route string, so renaming it in SipatokkongMain.kt breaks a test here rather
        // than producing a tap that silently goes nowhere.
        assertEquals("feedback_thread/fb-1", PushPayload.routeFor(PushPayload.parse(feedbackPush())))
    }

    @Test
    fun `a task push opens nothing`() {
        // Not an oversight: tugas_detail needs an occurrence date the notification has never
        // carried, and a recurring program has dozens with no single right answer.
        val data = push(
            "notification_id" to "n-2",
            "type" to "tugas_baru",
            "title" to "Kak Dewi memberi tugas untuk Andi",
            "entity_table" to "tugas",
            "entity_id" to "t-1",
        )
        assertNull(PushPayload.routeFor(PushPayload.parse(data)))
    }

    @Test
    fun `a feedback push with no entity id opens nothing`() {
        assertNull(PushPayload.routeFor(PushPayload.parse(feedbackPush(entityId = null))))
    }

    @Test
    fun `a whitespace entity id opens nothing`() {
        assertNull(PushPayload.routeFor(PushPayload.parse(feedbackPush(entityId = "   "))))
    }

    @Test
    fun `an unknown entity table opens nothing`() {
        val data = push("type" to "feedback_baru", "entity_table" to "dokumentasi", "entity_id" to "d-1")
        assertNull(PushPayload.routeFor(PushPayload.parse(data)))
    }

    @Test
    fun `a null message has no route`() {
        assertNull(PushPayload.routeFor(null))
        assertNull(PushPayload.routeFor(PushPayload.parse(emptyMap())))
    }

    @Test
    fun `a push routes if and only if the inbox would deep-link the same row`() {
        // The invariant this whole file exists for. The tray and the list must never disagree about
        // where a row leads; both read entity_table/entity_id through NotifDisplay.targetOf.
        val cases = listOf(
            "feedback" to "fb-1",
            "feedback" to "",
            "tugas" to "t-1",
            "dokumentasi" to "d-1",
            "" to "x",
        )
        for ((table, id) in cases) {
            val data = buildMap {
                put("type", "feedback_baru")
                put("entity_table", table)
                put("entity_id", id)
            }
            val routes = PushPayload.routeFor(PushPayload.parse(data)) != null
            val deepLinks = NotifDisplay.targetOf(table, id) is NotifTarget.FeedbackThread
            assertEquals("$table/$id", deepLinks, routes)
        }
    }

    // ---- tray id ----

    @Test
    fun `the same row always lands in the same tray slot`() {
        // FCM re-delivers. Without a stable id the user gets the same notification twice.
        val first = PushPayload.notificationIdFor(PushPayload.parse(feedbackPush()))
        val second = PushPayload.notificationIdFor(PushPayload.parse(feedbackPush()))
        assertEquals(first, second)
    }

    @Test
    fun `two different rows do not collide`() {
        val a = PushPayload.parse(feedbackPush() + ("notification_id" to "8f14e45f-ea1e-4a1b-9c2d-000000000001"))
        val b = PushPayload.parse(feedbackPush() + ("notification_id" to "8f14e45f-ea1e-4a1b-9c2d-000000000002"))
        assertNotEquals(PushPayload.notificationIdFor(a), PushPayload.notificationIdFor(b))
    }

    @Test
    fun `a payload with no notification id still posts somewhere`() {
        val noId = PushPayload.parse(push("type" to "feedback_baru", "entity_table" to "feedback", "entity_id" to "fb-9"))
        PushPayload.notificationIdFor(noId)   // must not throw
        PushPayload.notificationIdFor(null)   // nor must this
    }

    // ---- should we ask for the permission ----

    @Test
    fun `below API 33 we never ask, however things look`() {
        // The false-toast bug. There, checkSelfPermission reports DENIED for a permission that does
        // not exist, launch() shows no dialog, and the callback says granted = false -- so without
        // this branch every device on 27-32 gets an apology about nothing.
        assertFalse(PushPermission.shouldAsk(isRequired = false, alreadyGranted = false, alreadyAsked = false))
        assertFalse(PushPermission.shouldAsk(isRequired = false, alreadyGranted = false, alreadyAsked = true))
    }

    @Test
    fun `an already granted permission is not asked for again`() {
        assertFalse(PushPermission.shouldAsk(isRequired = true, alreadyGranted = true, alreadyAsked = false))
    }

    @Test
    fun `a first run on API 33 asks`() {
        assertTrue(PushPermission.shouldAsk(isRequired = true, alreadyGranted = false, alreadyAsked = false))
    }

    @Test
    fun `a second run does not nag`() {
        // After two system denials launch() returns instantly with false and shows nothing, so a
        // second attempt is not another chance -- only another toast.
        assertFalse(PushPermission.shouldAsk(isRequired = true, alreadyGranted = false, alreadyAsked = true))
    }

    @Test
    fun `granted beats not yet asked`() {
        assertFalse(PushPermission.shouldAsk(isRequired = true, alreadyGranted = true, alreadyAsked = true))
    }

    // ---- how the tray should draw it ----

    @Test
    fun `a note and a reply are drawn as messages`() {
        // After migration 0010 the title of these two kinds IS the sender's name, which is what
        // makes the messaging layout honest rather than decorative.
        assertEquals(PushStyle.MESSAGING, PushPayload.styleFor(PushPayload.parse(feedbackPush())!!))
        assertEquals(
            PushStyle.MESSAGING,
            PushPayload.styleFor(PushPayload.parse(feedbackPush(type = "feedback_baru"))!!),
        )
    }

    @Test
    fun `a task is expandable, not a message`() {
        // A task is a program, not somebody writing to you -- and after 0010 its body is
        // "judul - description", which routinely runs past one line and needs the expand.
        val data = push(
            "notification_id" to "n-2",
            "type" to "tugas_baru",
            "title" to "Kak Dewi memberi tugas untuk Andi",
            "body" to "Latihan Sensori - Ajak anak menyentuh tekstur kain selama dua menit.",
            "entity_table" to "tugas",
            "entity_id" to "t-1",
        )
        assertEquals(PushStyle.EXPANDABLE, PushPayload.styleFor(PushPayload.parse(data)!!))
    }

    @Test
    fun `an unrecognised kind is expandable too`() {
        val data = push("type" to "kabar_gembira", "title" to "Halo", "body" to "Ada kabar baru.")
        assertEquals(PushStyle.EXPANDABLE, PushPayload.styleFor(PushPayload.parse(data)!!))
    }

    @Test
    fun `nothing to say is drawn plain`() {
        // Load-bearing, not padding: MessagingStyle with zero messages renders an empty
        // conversation. Reachable whenever bodyLineOf drops the CATATAN_RUTIN filler -- any row
        // written before 0010 started substituting mood and chips at the source.
        val filler = PushPayload.parse(feedbackPush(body = CATATAN_RUTIN))!!
        assertEquals(PushStyle.PLAIN, PushPayload.styleFor(filler))
        assertEquals(PushStyle.PLAIN, PushPayload.styleFor(PushPayload.parse(feedbackPush(body = null))!!))
    }
}
