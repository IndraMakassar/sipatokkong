package com.antoniusindra.sipatokkong.core.push

import android.util.Log
import com.antoniusindra.sipatokkong.core.auth.SessionManager
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps `public.push_tokens` in step with who is signed in on this device.
 *
 * Two rules, and the second one is the interesting half:
 *
 * 1. **Register on the transition into [SessionManager.State.SignedIn].**
 * 2. **Never delete in reaction to a state change.** Deletion is only ever the explicit
 *    [SessionManager.signOut] action.
 *
 * Rule 2 exists because `SessionManager` maps `RefreshFailure` to `SignedOut` as well — so a
 * registrar that unregistered on every `SignedOut` would throw away the device's registration
 * whenever the network wobbled, and nothing would put it back until the next fresh login. Making
 * deletion an action rather than a reaction removes that whole class of problem instead of
 * special-casing it.
 *
 * There is deliberately **no local cache of the last token**: sign-in asks Firebase for the current
 * one independently of [onTokenRefreshed], so the two paths overlap through the same upsert and
 * there is no staleness to reason about.
 */
class PushRegistrar(
    private val client: SupabaseClient,
    private val sessionManager: SessionManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val currentUserId: String?
        get() = (sessionManager.state.value as? SessionManager.State.SignedIn)?.profile?.id

    /** Registers this device for whoever is signed in now. Safe to call when nobody is. */
    fun registerCurrentDevice() {
        if (currentUserId == null) return
        scope.launch {
            runCatching { register(currentToken()) }
                .onFailure { Log.w(PUSH_LOG_TAG, "token register failed: ${it.message}") }
        }
    }

    /** From [SipatokkongMessagingService.onNewToken]; ignored when nobody is signed in. */
    fun onTokenRefreshed(token: String) {
        if (currentUserId == null) return
        scope.launch {
            runCatching { register(token) }
                .onFailure { Log.w(PUSH_LOG_TAG, "token refresh register failed: ${it.message}") }
        }
    }

    /**
     * Releases this device. Must run **before** `auth.signOut()`.
     *
     * `unregister_push_token` gates on `auth.uid()`, which is gone the instant the session is — so
     * called afterwards this matches no rows and fails silently, leaving the previous user
     * registered on a phone somebody else is about to use.
     */
    suspend fun unregisterCurrentDevice() {
        client.postgrest.rpc("unregister_push_token", tokenArg(currentToken()))
    }

    private suspend fun register(token: String) {
        client.postgrest.rpc("register_push_token", tokenArg(token))
    }

    /** The argument name has to match the SQL parameter `p_token` exactly. */
    private fun tokenArg(token: String) = buildJsonObject { put("p_token", token) }

    /**
     * The device's current FCM token.
     *
     * Bridged by hand rather than adding `kotlinx-coroutines-play-services` for one call — this app
     * has a hard data-frugality requirement and the dependency would earn nothing else.
     */
    private suspend fun currentToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("FCM token unavailable")
                )
            }
        }
    }
}
