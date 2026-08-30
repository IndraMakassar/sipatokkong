package com.antoniusindra.sipatokkong.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.antoniusindra.sipatokkong.core.push.PushPermission

/**
 * Asks for notification permission once, after the user has arrived somewhere that explains why.
 *
 * Shares its ingredients with [rememberMediaSaveGate] — `checkSelfPermission`,
 * `ActivityResultContracts.RequestPermission()`, an Indonesian toast on denial, an SDK escape hatch
 * — but not its shape, deliberately. That one wraps an action because the user pressed a button
 * and is owed an outcome. Here nobody asked for anything, so this fires from a `LaunchedEffect` on
 * a home screen **after sign-in**, never at app launch: a permission dialog shown before someone
 * knows what the app is gets dismissed by reflex, and Android only offers the prompt twice.
 *
 * Asked once per process. A `DataStore` flag would survive restarts, but two system-level denials
 * already make `launch()` return instantly without a dialog, so the practical effect of asking
 * again is a second toast and nothing else — [PushPermission.shouldAsk] is where that rule lives
 * and it is unit-tested there.
 */
@Composable
fun NotificationPermissionEffect() {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                "Izin notifikasi ditolak, jadi pemberitahuan tidak akan muncul di layar. " +
                    "Daftar notifikasi di dalam aplikasi tetap bisa dibuka.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (PushPermission.shouldAsk(PushPermission.isRequired, granted, asked)) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
