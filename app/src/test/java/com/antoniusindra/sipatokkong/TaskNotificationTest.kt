package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.push.PushPayload
import com.antoniusindra.sipatokkong.data.model.Notification
import com.antoniusindra.sipatokkong.ui.notifications.NotifDisplay
import com.antoniusindra.sipatokkong.ui.notifications.NotifTarget
import com.antoniusindra.sipatokkong.ui.notifications.NotifType
import org.junit.Assert.*
import org.junit.Test

class TaskNotificationTest {
    @Test
    fun `submission and evaluation inbox and push open the same exact completion`() {
        for ((raw, kind) in listOf("tugas_setoran" to NotifType.TUGAS_SETORAN, "tugas_evaluasi" to NotifType.TUGAS_EVALUASI)) {
            val row = Notification("notif-1", "recipient", raw, entityTable = "tugas_penyelesaian", entityId = "completion-1")
            val item = NotifDisplay.from(row)
            val push = PushPayload.parse(mapOf(
                "type" to raw, "entity_table" to "tugas_penyelesaian", "entity_id" to "completion-1",
            ))!!
            assertEquals(kind, item.type)
            assertEquals(NotifTarget.SubmissionDetail("completion-1"), item.target)
            assertEquals(item.target, push.target)
            assertEquals("submission_detail/completion-1", PushPayload.routeFor(push))
            assertEquals(PushPayload.trayIdForEntity("completion-1"), PushPayload.notificationIdFor(push))
        }
    }

    @Test
    fun `different dates of a program retain separate completion notifications`() {
        fun message(id: String) = PushPayload.parse(mapOf(
            "type" to "tugas_evaluasi", "entity_table" to "tugas_penyelesaian", "entity_id" to id,
        ))!!
        assertNotEquals(PushPayload.routeFor(message("day-1")), PushPayload.routeFor(message("day-2")))
        assertNotEquals(PushPayload.notificationIdFor(message("day-1")), PushPayload.notificationIdFor(message("day-2")))
    }

    @Test
    fun `missing completion identity does not open a broken route`() {
        assertEquals(NotifTarget.None, NotifDisplay.targetOf("tugas_penyelesaian", " "))
    }
}
