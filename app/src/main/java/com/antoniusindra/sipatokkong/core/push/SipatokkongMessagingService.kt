package com.antoniusindra.sipatokkong.core.push

import android.util.Log
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives pushes from FCM.
 *
 * Every message this app sends is **data-only** — no `notification` block — and that is deliberate.
 * With a `notification` block the SDK builds and posts the notification itself whenever the app is
 * backgrounded and [onMessageReceived] is never called, so behaviour splits across two code paths:
 * the SDK's in background, ours in foreground. That split is the single biggest source of "it worked
 * in development" in FCM, and it would also mean the tap-routing worked two different ways.
 *
 * Data-only runs one path in both states, which is what lets the parsing and routing live in
 * [PushPayload] as pure functions with real tests behind them.
 *
 * The cost, stated plainly: a data message only survives Doze at high priority, so the Edge Function
 * sets `android.priority = HIGH` on every send. And an app in the stopped state — force-stopped, or
 * swiped away on some manufacturer skins — receives nothing at all, which no payload choice fixes.
 */
class SipatokkongMessagingService : FirebaseMessagingService() {

    /**
     * A new registration token, on install, on rotation, or after the user clears app data.
     *
     * This can fire while signed out — on a fresh install it usually does, before anyone has logged
     * in — so [PushRegistrar] drops it when there is no session rather than guessing an owner.
     */
    override fun onNewToken(token: String) {
        Log.i(PUSH_LOG_TAG, "new FCM token")
        ServiceLocator.pushRegistrar.onTokenRefreshed(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val parsed = PushPayload.parse(message.data)
        if (parsed == null) {
            Log.w(PUSH_LOG_TAG, "push with no usable payload, ignored")
            return
        }
        PushNotifier.show(this, parsed)
    }
}
