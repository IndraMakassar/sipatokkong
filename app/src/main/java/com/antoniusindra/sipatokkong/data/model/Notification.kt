package com.antoniusindra.sipatokkong.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A row of `public.notifications` — the per-user inbox.
 *
 * Written **only** by the three `security definer` triggers in `0001_initial_schema.sql`
 * (`on_tugas_insert`, `on_feedback_insert`, `on_balasan_insert`). There is no client INSERT policy
 * and no DELETE policy at all, so from the app's side this table is read-only apart from the single
 * field in [NotificationReadUpdate].
 *
 * [type] is a **String, not a Kotlin enum**, and that is the load-bearing decision in this file.
 * `kotlinx.serialization` throws on an unknown enum constant, and it throws inside `decodeList` --
 * so one unrecognised row would take down the entire inbox rather than one card. The Postgres enum
 * `notif_type` already declares three values nothing currently writes (`tugas_reminder`,
 * `tugas_missed`, `presensi`); the day a cron job starts writing one of them, a parent still on an
 * older build must keep seeing their notifications. The String is narrowed to a UI kind by
 * `NotifDisplay.kindOf`, which has a fallback. Same defensive posture as [Presensi.parsedDate].
 *
 * [title], [body], [entityTable] and [entityId] are nullable because the columns are, even though
 * all three current triggers always fill them.
 */
@Serializable
data class Notification(
    val id: String,
    @SerialName("recipient_id") val recipientId: String,
    val type: String,
    val title: String? = null,
    val body: String? = null,
    @SerialName("entity_table") val entityTable: String? = null,
    @SerialName("entity_id") val entityId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * The only write this app makes to `notifications`.
 *
 * A dedicated one-field payload rather than the decoded [Notification] echoed back, because
 * `notif_update` gates the **row** (`recipient_id = auth.uid()`), not the **columns**. PATCHing the
 * whole object would legally rewrite `title`, `body`, `type` and `created_at` with whatever the
 * phone last decoded, clobbering anything the server changed in between. Mirrors [TaskStopUpdate].
 */
@Serializable
data class NotificationReadUpdate(
    @SerialName("is_read") val isRead: Boolean = true,
)
