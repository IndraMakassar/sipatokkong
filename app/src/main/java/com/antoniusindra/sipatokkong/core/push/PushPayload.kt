package com.antoniusindra.sipatokkong.core.push

import com.antoniusindra.sipatokkong.data.model.Notification
import com.antoniusindra.sipatokkong.ui.notifications.NotifDisplay
import com.antoniusindra.sipatokkong.ui.notifications.NotifTarget
import com.antoniusindra.sipatokkong.ui.notifications.NotifType

/** Keys in the FCM `data` map. Written by the `push-notify` Edge Function, one per column it sends. */
private const val KEY_NOTIFICATION_ID = "notification_id"
private const val KEY_TYPE = "type"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"
private const val KEY_ENTITY_TABLE = "entity_table"
private const val KEY_ENTITY_ID = "entity_id"

/** One push, as the tray will draw it. Mirrors what the inbox shows for the same row. */
data class PushMessage(
    val type: NotifType,
    val title: String,
    val body: String?,
    val target: NotifTarget,
    val notificationId: String?,
)

/**
 * What arrives over FCM, and where tapping it goes.
 *
 * A push carries a copy of one `public.notifications` row, so the tray and the in-app inbox are
 * drawing the same thing from the same fields — and this file exists to make sure they cannot
 * disagree about it. Everything here delegates to [NotifDisplay]: the kind, the fallback wording and
 * the destination are decided in exactly one place, and a push that worded a row differently from
 * the list would be a bug the user could see.
 *
 * The payload is deliberately untrusted input in the shape of a `Map<String, String>`: FCM strips
 * types, the Edge Function omits null columns entirely, and an APK in the field can be older than
 * the function sending to it. Nothing here may throw.
 *
 * Kept free of Android types so the rules are unit-testable — the suite has no Robolectric, so
 * `NotificationManagerCompat`, `PendingIntent` and `Build.VERSION` cannot be covered at all. The
 * same split `MediaNaming` makes for downloads.
 */
object PushPayload {

    /**
     * Reads the FCM data map, or null when there is nothing worth showing.
     *
     * Title and body run through the same fallbacks the inbox uses, by routing a synthetic
     * [Notification] through [NotifDisplay.from] rather than reimplementing them. The tray and the
     * card therefore resolve one row to one wording, whatever the trigger wrote — which since
     * migration `0010` is the sender's and child's name rather than a constant. A body of
     * `CATATAN_RUTIN` is dropped in the tray for the same reason it is dropped in the list: a
     * volunteer would otherwise get the identical sentence over and over.
     */
    fun parse(data: Map<String, String>): PushMessage? {
        if (data.isEmpty()) return null
        val item = NotifDisplay.from(
            Notification(
                // Neither id is used for display; they exist because the model requires them.
                id = data[KEY_NOTIFICATION_ID].orEmpty(),
                recipientId = "",
                type = data[KEY_TYPE].orEmpty(),
                title = data[KEY_TITLE],
                body = data[KEY_BODY],
                entityTable = data[KEY_ENTITY_TABLE],
                entityId = data[KEY_ENTITY_ID],
            )
        )
        return PushMessage(
            type = item.type,
            title = item.title,
            body = item.body,
            target = item.target,
            notificationId = data[KEY_NOTIFICATION_ID]?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * The nav route a tap should open, or null when there is nowhere honest to go.
     *
     * The push carries `entity_table`/`entity_id`, never a route string, so [NotifDisplay.targetOf]
     * decides this and a future destination is added in one place rather than two.
     *
     * The task route carries no date, and this is the function that could not supply one even if it
     * wanted to: it runs inside `SipatokkongMessagingService` while building a `PendingIntent`, and
     * a suspend `getTask()` is not available there. That constraint is why `tugas_detail` declares
     * `occurrenceDate` optional rather than the inbox resolving a date before navigating -- a design
     * that resolved here would work for a tapped card and not for a tapped push, and those two must
     * stay identical.
     */
    fun routeFor(message: PushMessage?): String? = when (val target = message?.target) {
        is NotifTarget.FeedbackThread -> "feedback_thread/${target.feedbackId}"
        is NotifTarget.TaskDetail -> "tugas_detail/${target.tugasId}"
        NotifTarget.None, null -> null
    }

    /**
     * The tray slot this push occupies.
     *
     * Stable for a given notification row, so FCM re-delivering the same row replaces the entry
     * instead of stacking a second identical one. Falls back through the entity id to a constant, so
     * a payload missing both still posts somewhere rather than throwing.
     *
     * The fallback reads the id out of every target rather than only the feedback one. As an
     * `as? FeedbackThread` it silently skipped task rows, so two task pushes arriving without a
     * `notification_id` shared [FALLBACK_TRAY_ID] and the second replaced the first in the tray --
     * a `when` over the sealed type makes that omission a compile error the next time a target is
     * added.
     */
    fun notificationIdFor(message: PushMessage?): Int {
        val seed = message?.notificationId ?: when (val target = message?.target) {
            is NotifTarget.FeedbackThread -> target.feedbackId
            is NotifTarget.TaskDetail -> target.tugasId
            NotifTarget.None, null -> null
        } ?: return FALLBACK_TRAY_ID
        return seed.hashCode()
    }

    private const val FALLBACK_TRAY_ID = 1

    /**
     * How the tray should draw this push.
     *
     * [PLAIN] when there is no body is **load-bearing, not defensive padding**: `MessagingStyle`
     * with zero messages renders an empty conversation, and `BigTextStyle` with nothing to expand
     * gives a chevron onto blank space. A null body is genuinely reachable — [NotifDisplay
     * .bodyLineOf] drops the `CATATAN_RUTIN` filler, so any row written before migration 0010
     * substituted mood and chips at the source arrives here with nothing to say.
     */
    fun styleFor(message: PushMessage): PushStyle = when {
        message.body.isNullOrBlank() -> PushStyle.PLAIN
        message.type == NotifType.FEEDBACK_BARU ||
            message.type == NotifType.FEEDBACK_BALASAN -> PushStyle.MESSAGING
        else -> PushStyle.EXPANDABLE
    }
}

/**
 * The three shapes a push can take in the tray.
 *
 * [MESSAGING] is for the two kinds that really are one person writing to another; after migration
 * 0010 their title *is* the sender's name, which is what makes the messaging layout honest rather
 * than decorative. A task is not a message from a person, so it gets [EXPANDABLE] instead.
 */
enum class PushStyle { MESSAGING, EXPANDABLE, PLAIN }
