package com.antoniusindra.sipatokkong.core.auth

import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observes the Supabase auth session and resolves the current user's [Profile] (with role)
 * from `public.profiles`. Role drives which module graph the app routes to after login.
 *
 * Note: the client-side role is for UX/routing only — real authorization is enforced by RLS.
 */
class SessionManager(private val client: SupabaseClient) {

    sealed interface State {
        data object Loading : State
        data object SignedOut : State
        data class SignedIn(val profile: Profile) : State
        data class Error(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> loadProfile(status.session.user?.id)
                    is SessionStatus.NotAuthenticated -> apply(
                        if (status.isSignOut) SessionTransition.Signal.SIGNED_OUT_EXPLICIT
                        else SessionTransition.Signal.SIGNED_OUT_IMPLICIT
                    )
                    is SessionStatus.Initializing ->
                        apply(SessionTransition.Signal.INITIALIZING)
                    is SessionStatus.RefreshFailure ->
                        apply(SessionTransition.Signal.REFRESH_FAILED)
                }
            }
        }
    }

    fun retry() {
        val status = client.auth.currentSessionOrNull()
        if (status != null) {
            scope.launch { loadProfile(status.user?.id) }
        } else {
            _state.value = State.SignedOut
        }
    }

    /** Applies a signal, leaving the state alone when [SessionTransition] says to. */
    private fun apply(signal: SessionTransition.Signal) {
        SessionTransition.next(_state.value, signal)?.let { _state.value = it }
    }

    private suspend fun loadProfile(userId: String?) {
        if (userId == null) {
            apply(SessionTransition.Signal.SIGNED_OUT_IMPLICIT)
            return
        }

        val next = runCatching {
            val profile = client.postgrest.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<Profile>()

            if (profile != null) {
                State.SignedIn(profile)
            } else {
                // The auth user exists but has no `profiles` row — a broken account, not a
                // transient failure, so it is worth showing even over a working session.
                State.Error("Profil tidak ditemukan. Pastikan data profil sudah dibuat di database.")
            }
        }.getOrElse {
            // Only the first failure becomes an error pane. This runs again on every app reopen,
            // so on a bad connection a reload failure would otherwise replace a working screen
            // with full-screen Retry. See SessionTransition.onProfileLoadFailed.
            SessionTransition.onProfileLoadFailed(_state.value, it.message ?: "Gagal memuat profil")
                ?: return
        }

        _state.value = next

        // After the assignment, not inside it: PushRegistrar reads this same state to learn who it
        // is registering for, so calling it while _state is still the previous value would quietly
        // do nothing. Covers a fresh login, retry(), and a session restored at launch alike.
        if (_state.value is State.SignedIn) {
            ServiceLocator.pushRegistrar.registerCurrentDevice()
        }
    }

    val currentRole: UserRole?
        get() = (_state.value as? State.SignedIn)?.profile?.role

    suspend fun signOut() {
        // Must precede auth.signOut(): `unregister_push_token` gates on auth.uid(), which is gone
        // the instant the session is, so afterwards the delete matches nothing and fails silently —
        // leaving this user registered on a phone the next person is about to sign in to.
        //
        // Best-effort on purpose. Someone with no signal must still be able to sign out, or a
        // shared phone stays logged in as the previous parent. The token table self-heals anyway:
        // the next sign-in on this device takes the row over (push_tokens is keyed on the token).
        runCatching { ServiceLocator.pushRegistrar.unregisterCurrentDevice() }
        client.auth.signOut()
    }
}
