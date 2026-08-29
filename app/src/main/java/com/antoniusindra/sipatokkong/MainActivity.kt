package com.antoniusindra.sipatokkong

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        super.onCreate(savedInstanceState)
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
}
