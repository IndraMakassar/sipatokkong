package com.antoniusindra.sipatokkong

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.core.push.EXTRA_PUSH_ROUTE
import com.antoniusindra.sipatokkong.ui.SipatokkongMain
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /**
     * The destination a tapped notification asked for, held until the nav graph can act on it.
     *
     * A flow rather than a plain value because the tap can arrive in two different ways — a cold
     * start through [onCreate] and a warm one through [onNewIntent] — and the composition has to
     * react to the second without being rebuilt.
     */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(), not after: installSplashScreen() swaps the window from
        // Theme.Sipatokkong.Splash to postSplashScreenTheme, and it has to do that while the window
        // is still being created. Called later it either throws or silently does nothing depending
        // on API level — and "silently does nothing" is how a splash regression ships unnoticed.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash until SessionManager has decided who this is, which is the same moment
        // SipatokkongMain can name a start destination. Without it there are three visual states
        // for one wait: splash, then a bare spinner, then the home screen.
        //
        // Bounded, because "until the session resolves" is forever with no signal. A held splash
        // swallows touches, so a splash with no way out is the one thing worse than a spinner —
        // past the bound the composition falls through to its own pane, which can become the Error
        // branch and offer Retry.
        val heldFrom = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - heldFrom < SPLASH_MAX_HOLD_MS &&
                ServiceLocator.sessionManager.state.value is SessionManager.State.Loading
        }

        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_PUSH_ROUTE)
        setContent {
            SipatokkongTheme {
                val route by pendingRoute.collectAsStateWithLifecycle()
                SipatokkongMain(
                    pendingRoute = route,
                    onRouteConsumed = { pendingRoute.value = null },
                )
            }
        }
    }

    /** Reached instead of a second Activity because of `launchMode="singleTop"` in the manifest. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Without this, getIntent() keeps handing back the intent that originally launched the
        // Activity, so a second notification tap would silently replay the first one's destination.
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_PUSH_ROUTE)
    }

    private companion object {
        /**
         * How long the splash may cover an unresolved session.
         *
         * Two seconds, and it is a ceiling on staring at a logo rather than an estimate of the
         * work. An earlier version of this comment called the wait "an auth-storage read plus one
         * `profiles` SELECT" and that was wrong: measured signed *out* on emulator-5554, where no
         * network is involved at all, resolution takes 3.3-3.9 s of class loading (see
         * `SipatokkongApp.warmSession`). So this bound is regularly exceeded on a debug build and
         * the composition falls through to its own pane.
         *
         * Deliberately not raised to cover that number. Past the bound the user needs the in-app
         * Retry button, not a longer frozen logo — and the fix for the 3 s is R8 on release
         * builds, not a bigger constant here.
         */
        const val SPLASH_MAX_HOLD_MS = 2_000L
    }
}
