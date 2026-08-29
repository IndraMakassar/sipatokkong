package com.antoniusindra.sipatokkong

import android.app.Application
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.push.PushNotifier

/**
 * Application entry point. Initializes the manual DI [ServiceLocator]
 * (no Hilt for the pilot — kept intentionally lightweight).
 */
class SipatokkongApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Before anything can deliver a push. FCM starts the process through this method, so
        // creating the channel here means it always exists by the time onMessageReceived runs —
        // see PushNotifier.ensureChannel for what goes wrong if it does not.
        PushNotifier.ensureChannel(this)
    }
}
