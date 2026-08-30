package com.antoniusindra.sipatokkong

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.push.PushNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
        warmSession()
    }

    /**
     * Starts resolving the session here, so it runs *underneath* the splash instead of after it.
     *
     * [ServiceLocator.sessionManager] is a `by lazy` and [ServiceLocator.init] only stores a
     * Context, so before this existed nothing constructed the Supabase client until the first
     * composition read the session state — on the main thread. Measured on `emulator-5554`
     * (API 37, debug build, no stored session), by logging which thread ran the lazy initialiser:
     *
     * ```
     * without warmSession():  thread=main  943 / 957 / 983 ms
     * with warmSession():     thread=main  202 / 293 / 348 ms
     * ```
     *
     * So this is not a micro-optimisation: it takes roughly 700 ms of class loading off the main
     * thread during cold start, which is the thread the splash and the first frame need.
     *
     * It does not reliably make the background thread *win* the race — `by lazy` is `SYNCHRONIZED`
     * and main frequently still runs the initialiser. The gain is overlap: the loading is already
     * under way when main arrives, so main pays the remainder rather than the whole. Touching
     * [ServiceLocator.supabase] first was tried and measured no better (~340 ms), so the simpler
     * form is kept.
     *
     * Touching the lazy is therefore the entire point of this function. The log line exists so that
     * touch cannot be mistaken for dead code and deleted, and so the remaining wait is a number
     * somebody can read out of logcat instead of a guess. It records a duration and nothing about
     * who signed in — `SessionManager` used to print the auth uid and that was a defect.
     *
     * What this does **not** fix: the total resolution still measured ~3.3-3.9 s on that emulator,
     * split roughly half building the Supabase client and half inside Auth, all of it class
     * loading rather than network. The lever for that is R8 — `app/build.gradle.kts` still carries
     * the AGP project template's `optimization { enable = false }` on release, so the shipped APK
     * loads the whole unshrunk Ktor and supabase-kt graph. Turning it on needs a release signing
     * config and Proguard rules for kotlinx-serialization, neither of which exists yet.
     */
    private fun warmSession() {
        val startedAt = SystemClock.elapsedRealtime()
        ServiceLocator.appScope.launch {
            val session = ServiceLocator.sessionManager
            session.state.first { it !is SessionManager.State.Loading }
            Log.d(STARTUP_LOG_TAG, "sesi selesai dimuat dalam ${SystemClock.elapsedRealtime() - startedAt} ms")
        }
    }

    private companion object {
        const val STARTUP_LOG_TAG = "SipatokkongStartup"
    }
}
