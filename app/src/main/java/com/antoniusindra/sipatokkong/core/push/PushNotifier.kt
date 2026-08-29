package com.antoniusindra.sipatokkong.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.content.ContextCompat
import com.antoniusindra.sipatokkong.MainActivity
import com.antoniusindra.sipatokkong.R

const val PUSH_LOG_TAG = "SipatokkongPush"

/** Extras carried from a tapped notification into [MainActivity]. */
const val EXTRA_PUSH_ROUTE = "push_route"

/**
 * Builds and posts the tray notification for one push.
 *
 * Everything Android-bound about push lives here, which is the other half of the split
 * [PushPayload] makes: that file holds the rules and is unit-tested, this one holds the calls that
 * cannot be. Nothing here decides anything — it renders what `PushPayload.parse` already decided.
 */
object PushNotifier {

    /**
     * One channel for everything this app sends.
     *
     * Not one per `notif_type`. Three channels is three switches in system settings, and these
     * users are exactly the people for whom a half-configured notification setting becomes "the app
     * is broken". The cost is real and worth stating: muting this channel mutes task notifications
     * along with feedback replies.
     */
    const val CHANNEL_ID = "sipatokkong_notifikasi"

    /**
     * Creates the channel. Called from `SipatokkongApp.onCreate`, and it must stay there.
     *
     * FCM starts the process through `Application.onCreate` before delivering, so creating it there
     * guarantees the channel exists before the first `onMessageReceived`. Created lazily inside the
     * service instead, the very first push after install can post to a channel that does not exist
     * yet and be dropped in silence — no crash, nothing in logcat that reads like an error.
     *
     * `minSdk` is 27, so `NotificationChannel` always exists here. No version guard is needed and
     * none should be added.
     */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pemberitahuan",
            // HIGH so a notification about a child's therapy makes a sound and shows as a heads-up.
            // Arriving silently in the tray is the failure this whole feature exists to fix.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Tugas baru, catatan dari orang tua, dan balasan feedback."
        }
        ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /** Renders one parsed push into the tray. Silently does nothing if the user denied permission. */
    fun show(context: Context, message: PushMessage) {
        val trayId = PushPayload.notificationIdFor(message)
        val route = PushPayload.routeFor(message)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            route?.let { putExtra(EXTRA_PUSH_ROUTE, it) }
        }
        val pending = PendingIntent.getActivity(
            context,
            // Same id as the tray slot: with FLAG_UPDATE_CURRENT, a shared request code would make
            // every notification hand back one another's extras.
            trayId,
            intent,
            // FLAG_IMMUTABLE is mandatory on API 31+ — getActivity throws without it.
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val base = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(message.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // On a SECURE lock screen this redacts the entire notification — sender, avatar and
            // message alike — down to the app name. Not just the body: an earlier version of this
            // comment claimed the title still showed, and it does not. That is the reason a
            // parent's name in the title costs nothing here.
            //
            // On a lock screen with no PIN there is no redaction at all, and nothing in this API
            // changes that.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pending)

        val notification = when (PushPayload.styleFor(message)) {
            PushStyle.MESSAGING -> {
                val avatar = LetterAvatar.bitmap(context, message.title)
                // Required, and required to be non-blank: MessagingStyle writes it into
                // EXTRA_SELF_DISPLAY_NAME and some platform versions assert on a null. Never shown
                // here, because no message is ever added without a sender.
                val me = Person.Builder().setName("Saya").setKey("me").build()
                val sender = Person.Builder()
                    .setName(message.title)
                    .setKey(message.title)
                    // createWithBitmap, NOT createWithAdaptiveBitmap: the adaptive variant applies
                    // the adaptive-icon mask and its inset to art it assumes is full-bleed square,
                    // so an already-circular bitmap comes out as a shrunken disc inside a circle.
                    .setIcon(IconCompat.createWithBitmap(avatar))
                    .setImportant(true)
                    .build()

                base.setStyle(
                    NotificationCompat.MessagingStyle(me)
                        .setGroupConversation(false)
                        .addMessage(
                            NotificationCompat.MessagingStyle.Message(
                                message.body, System.currentTimeMillis(), sender,
                            )
                        )
                )
                    // Not redundant with the Person icon: androidx Person icons are only honoured
                    // from API 28, and minSdk here is 27. On that one level this is what shows.
                    .setLargeIcon(avatar)
                    .setContentText(message.body)
                    .build()
            }

            PushStyle.EXPANDABLE ->
                base.setContentText(message.body)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .setBigContentTitle(message.title)
                            .bigText(message.body)
                    )
                    .build()

            // Title only. Inventing a body on the client is exactly what NotifDisplay.bodyLineOf
            // refuses to do, and a bare line is honest.
            PushStyle.PLAIN -> base.build()
        }

        // On API 33+ without POST_NOTIFICATIONS this is a silent no-op rather than an exception, so
        // the check exists to make that visible in logcat instead of leaving a silent hole.
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.i(PUSH_LOG_TAG, "notification dropped: notifications are disabled for this app")
            return
        }
        runCatching { manager.notify(trayId, notification) }
            .onFailure { Log.w(PUSH_LOG_TAG, "notify failed: ${it.message}") }
    }
}
