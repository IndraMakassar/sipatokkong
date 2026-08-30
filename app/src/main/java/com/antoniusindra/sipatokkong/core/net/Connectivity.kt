package com.antoniusindra.sipatokkong.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat

/**
 * Whether this phone currently believes it has internet.
 *
 * `ACCESS_NETWORK_STATE` has been declared in the manifest since the first commit and never read.
 * This is the first thing to read it.
 *
 * **Used before expensive actions, not before every request.** Signing in, uploading proof, saving a
 * visit and sending feedback all cost the user a wait and, on an upload, real data — and failing
 * them after that wait, with a message they could have had immediately, is the specific complaint
 * this answers. Loads are deliberately left alone: they already end in a mapped message with a
 * "Coba Lagi" button beside it, and adding a system-service hop to every happy path would cost more
 * than it gives.
 *
 * **This is a hint, never a guarantee.** `NET_CAPABILITY_INTERNET` reports "connected" on a captive
 * portal that will answer every request with a login page, and a network can drop between this check
 * and the call it guards. So a `true` here means "worth trying"; it never means the call will
 * succeed, which is why [com.antoniusindra.sipatokkong.core.util.ErrorText] still has to classify
 * whatever comes back.
 *
 * Fails **open**: any error reading the system service returns true, so a phone this cannot
 * interrogate is allowed to try the request rather than being told it is offline when it is not.
 */
object Connectivity {

    fun isOnline(context: Context): Boolean = runCatching {
        val manager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return@runCatching true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}
